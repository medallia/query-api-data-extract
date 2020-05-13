package com.medallia.references.feedbackdatasync.service;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medallia.references.feedbackdatasync.model.ProcessedRecord;
import com.medallia.references.feedbackdatasync.model.QueryApiResponse;

import static com.medallia.references.feedbackdatasync.constants.GraphQlConstants.FINISH_DATE;
import static com.medallia.references.feedbackdatasync.constants.GraphQlConstants.SURVEY_ID;

/**
 * Service that synchronizes data from Medallia's Query API on a continuous
 * basis.
 */
@Component
public class SyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncService.class);

    private static final int MAX_RECORDS_PER_REQUEST = 1000;

    @Value("${medallia.queryapi.url}")
    private String queryApiUrl;

    @Value("${medallia.queryapi.num.records.per.request:1000}")
    private Integer requestedNumRecordsPerRequest;
    private int numRecordsPerRequest;

    @Value("${medallia.queryapi.default.start.timestamp.epoch:}")
    private String defaultStartTimestampString;
    private ZonedDateTime defaultStartTimestamp;

    @Value("${medallia.queryapi.default.start.surveyid:-1}")
    private String defaultStartSurveyId;

    @Value("${medallia.queryapi.field.initialfinishdate.epoch}")
    private String initialFinishDateField;

    @Value("${medallia.queryapi.field.surveyid:a_surveyid}")
    private String surveyIdField;

    @Value("${medallia.queryapi.graphql.filter:}")
    private String graphQlFilter;

    @Value("${medallia.queryapi.graphql.nodes:}")
    private String graphQlNodes;

    @Autowired
    private WebClient webClient;

    @Autowired
    private RecordProcessingService recordProcessingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RetryTemplate retryTemplate;

    private String query;

    private final Semaphore availableWorkers = new Semaphore(1, true);

    @PostConstruct
    private void postConstruct() {
        query = String.format(
            "query syncQuery(\n" +
            "    $endTimestamp: String!,\n" +
            "    $initialFinishDateField: ID!,\n" +
            "    $numRecordsPerRequest: Int!,\n" +
            "    $surveyIdField: ID!,\n" +
            "    $startSurveyId: String!,\n" +
            "    $startTimestamp: String!\n" +
            ") {\n" +
            "  feedback(\n" +
                 // Only pull a fixed number of records per page
            "    first: $numRecordsPerRequest\n" +
                 // Order by the finish date and survey id to keep in sync with
                 // the Medallia cache layer
            "    orderBy: [\n" +
            "      { direction: ASC  fieldId: $initialFinishDateField }\n" +
            "      { direction: ASC  fieldId: $surveyIdField }\n" +
            "    ]\n" +
            "    filter: { and: [\n" +
            "      %s\n" + // the business-logic segmentation filters
            "      { fieldIds: [ $initialFinishDateField ], lt: $endTimestamp }\n" +
            "      { or: [\n" +
            "        { fieldIds: [ $initialFinishDateField ], gt: $startTimestamp }\n" +
            "        { and: [\n" +
            "          { fieldIds: [ $initialFinishDateField ], gte: $startTimestamp }\n" +
            "          { fieldIds: [ $surveyIdField ], gt: $startSurveyId }\n" +
            "        ] }\n" +
            "      ] }\n" +
            "    ] }\n" +
            "  ) {\n" +
            "    totalCount\n" +
            "    nodes {\n" +
            "      " + SURVEY_ID + ": fieldData(fieldId: $surveyIdField) { values }\n" +
            "      " + FINISH_DATE + ": fieldData(fieldId: $initialFinishDateField) { values }\n" +
            "      %s\n" + // the custom fields needed
            "    }\n" +
            "  }\n" +
            "}",
            graphQlFilter,
            graphQlNodes
        );

        LOGGER.info("GraphQL template:\n{}", query);

        final boolean isRequestedNumRecordsPerRequestValid =
            requestedNumRecordsPerRequest == null ||
            requestedNumRecordsPerRequest <= 0;

        numRecordsPerRequest = Math.min(
            MAX_RECORDS_PER_REQUEST,
            isRequestedNumRecordsPerRequestValid
                ? MAX_RECORDS_PER_REQUEST
                : requestedNumRecordsPerRequest
        );

        LOGGER.info("Maximum records per request: {}", numRecordsPerRequest);

        defaultStartTimestamp = defaultStartTimestampString != null && defaultStartTimestampString.length() > 0
            ? ZonedDateTime.ofInstant(Instant.ofEpochSecond(Long.valueOf(defaultStartTimestampString)), ZoneOffset.UTC)
            : ZonedDateTime.now();
    }

    /**
     * Performs a periodic poll of data from the Medallia Query API.
     *
     * @throws JsonProcessingException if a serialization error occurs
     */
    @Scheduled(
        initialDelayString = "${medallia.queryapi.poll.initialdelay.msec}",
        fixedRateString = "${medallia.queryapi.poll.interval.msec}"
    )
    public void performPoll() throws JsonProcessingException {
        final boolean canProcess = availableWorkers.tryAcquire();
        if (!canProcess) {
            LOGGER.warn("Prior process still running, skipping this invocation");
            return;
        }

        try {
            LOGGER.info("Starting next scheduled pull");
            performQuery(ZonedDateTime.now());
        } finally {
            availableWorkers.release();
        }
    }

    /**
     * Queries a page of data from the Medallia Query API.
     *
     * @param endTimestamp the largest timestamp that should be pulled
     * @throws JsonProcessingException if a serialization error occurs
     */
    private void performQuery(
            final ZonedDateTime endTimestamp
    ) throws JsonProcessingException {
        LOGGER.info("Querying new records from Query API");

        final Optional<ProcessedRecord> lastProcessedRecord =
            recordProcessingService.getLastProcessedRecord();

        final String graphQlBody = getGraphQl(
            lastProcessedRecord,
            defaultStartTimestamp,
            endTimestamp
        );

        final QueryApiResponse response = retryTemplate.execute(arg0 -> {
            final String bodyAsString = webClient
                .post()
                    .uri(queryApiUrl)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .acceptCharset(Charset.forName("UTF-8"))
                    .body(BodyInserters.fromValue(graphQlBody))
                .retrieve()
                    .toEntity(String.class)
                    .block()
                .getBody();

            LOGGER.trace("GraphQL response:\n{}", bodyAsString);

            return objectMapper.readValue(bodyAsString, QueryApiResponse.class);
        });

        response.getData().getFeedback().getNodes().stream().forEach(node -> {
            recordProcessingService.processRecord(node);
        });

        final Long totalCount = response.getData().getFeedback().getTotalCount();
        if (totalCount >= numRecordsPerRequest) {
            LOGGER.info("Hit page max, pulling another page");
            performQuery(endTimestamp);
        } else {
            LOGGER.info("No more records to pull for this job");
        }
    }

    /**
     * Creates the Query API request's GraphQL.
     *
     * @param lastProcessedRecord the last record processed
     * @param defaultStartTimestamp the timestamp to use for the start if the
     *                              last record couldn't be found
     * @param endTimestamp the timestamp that marks the end of the range
     * @return the GraphQL to send to the Medallia Query API
     * @throws JsonProcessingException if serialization fails
     */
    private String getGraphQl(
            final Optional<ProcessedRecord> lastProcessedRecord,
            final ZonedDateTime defaultStartTimestamp,
            final ZonedDateTime endTimestamp
    ) throws JsonProcessingException {
        final ZonedDateTime startTimestamp = lastProcessedRecord
            .map(r -> r.getInitialFinishDate())
            .orElse(defaultStartTimestamp);

        final String surveyId = lastProcessedRecord
            .map(r -> r.getSurveyId().toString())
            .orElse(defaultStartSurveyId);

        final Map<String, Object> variables = new HashMap<>();
        variables.put("initialFinishDateField", initialFinishDateField);
        variables.put("numRecordsPerRequest", numRecordsPerRequest);
        variables.put("surveyIdField", surveyIdField);
        variables.put("startSurveyId", surveyId);
        variables.put("startTimestamp", startTimestamp.toEpochSecond());
        variables.put("endTimestamp", endTimestamp.toEpochSecond());

        final Map<String, Object> graphQl = new HashMap<>();
        graphQl.put("query", query);
        graphQl.put("variables", variables);

        final String graphQlString = objectMapper.writeValueAsString(graphQl);

        LOGGER.trace("GraphQL request:\n{}", graphQlString);

        return graphQlString;
    }

}
