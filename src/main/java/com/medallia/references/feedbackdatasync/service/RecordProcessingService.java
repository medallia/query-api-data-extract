package com.medallia.references.feedbackdatasync.service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medallia.references.feedbackdatasync.model.ProcessedRecord;
import com.medallia.references.feedbackdatasync.model.QueryApiResponse;

import static com.medallia.references.feedbackdatasync.constants.DbConstants.COL_INITIAL_FINISH_DATE;
import static com.medallia.references.feedbackdatasync.constants.DbConstants.COL_SURVEYID;
import static com.medallia.references.feedbackdatasync.constants.DbConstants.TABLE_RECORDS;
import static com.medallia.references.feedbackdatasync.constants.GraphQlConstants.FINISH_DATE;
import static com.medallia.references.feedbackdatasync.constants.GraphQlConstants.SURVEY_ID;

/**
 * Service that enables processing of records retrieved from the Medallia
 * Query API.
 */
@Component
public class RecordProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordProcessingService.class);

    private static final Logger RECORD_STREAM = LoggerFactory.getLogger("record-stream");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ReentrantReadWriteLock lastProcessedRecordLock = new ReentrantReadWriteLock();
    private volatile ProcessedRecord lastProcessedRecord = null;

    /**
     * Performs a periodic poll of data from the Medallia Query API.
     *
     * @param node the record to process
     */
    public void processRecord(final Map<String, QueryApiResponse.NodeValuesWrapper> node) {
        final BigInteger surveyId = new BigInteger(node.get(SURVEY_ID).getValues().get(0));

        final ZonedDateTime finishDate = ZonedDateTime.ofInstant(
            Instant.ofEpochSecond(Long.valueOf(node.get(FINISH_DATE).getValues().get(0))),
            ZoneOffset.UTC
        );

        LOGGER.info(
            "Processing survey {} (finishDate={}/{})",
            surveyId,
            finishDate,
            finishDate.toEpochSecond()
        );

        persistRecord(node);

        lastProcessedRecordLock.writeLock().lock();

        try {
            final boolean hasUpdate = lastProcessedRecord == null
                || lastProcessedRecord.getSurveyId().compareTo(surveyId) < 0
                || lastProcessedRecord.getInitialFinishDate().compareTo(finishDate) < 0;

            if (hasUpdate) {
                lastProcessedRecord = new ProcessedRecord(surveyId, finishDate);
            }
        } finally {
            lastProcessedRecordLock.writeLock().unlock();
        }
    }

    /**
     * Persists the record.  By default, this involves seriailizing the record
     * to a log stream.
     *
     * @param node the record to process
     */
    protected void persistRecord(final Map<String, QueryApiResponse.NodeValuesWrapper> node) {
        try {
            RECORD_STREAM.info(objectMapper.writeValueAsString(node));

            final BigInteger surveyId = new BigInteger(node.get(SURVEY_ID).getValues().get(0));

            final ZonedDateTime finishDate = ZonedDateTime.ofInstant(
                Instant.ofEpochSecond(Long.valueOf(node.get(FINISH_DATE).getValues().get(0))),
                ZoneOffset.UTC
            );

            jdbcTemplate.execute(
                "INSERT INTO " + TABLE_RECORDS + " " +
                "  (" + COL_SURVEYID + ", " + COL_INITIAL_FINISH_DATE + ") " +
                "VALUES " +
                "  (" + surveyId.toString() + ", " + finishDate.toEpochSecond() + ")"
            );
        } catch (JsonProcessingException e) {
            RECORD_STREAM.error("Unable to persist record: {}" + e.getMessage(), e);
            LOGGER.error("Unable to persist record: {}" + e.getMessage(), e);
        }
    }

    /**
     * Returns the last successfully-processed record.
     *
     * @return the last successfully-processed record
     */
    public Optional<ProcessedRecord> getLastProcessedRecord() {
        lastProcessedRecordLock.readLock().lock();
        try {
            if (lastProcessedRecord == null) {
                // Seed our process' cache with the last version available
                // in the database.  A local cache minimizes the need to
                // query the database for each batch pulled.  However,
                // other implementations for tracking this value are also
                // acceptable.

                LOGGER.info("Initializing last record from persistent storage");

                final List<ProcessedRecord> records = jdbcTemplate.query(
                    "SELECT " +
                    "  " + COL_SURVEYID + ", " +
                    "  " + COL_INITIAL_FINISH_DATE + " " +
                    "FROM " +
                    "  " + TABLE_RECORDS + " " +
                    "ORDER BY " +
                    "  " + COL_INITIAL_FINISH_DATE + " DESC, " +
                    "  " + COL_SURVEYID + " DESC " +
                    "LIMIT 1",
                    (rs, rowNum) -> new ProcessedRecord(
                        new BigInteger(rs.getString(COL_SURVEYID)),
                        ZonedDateTime.ofInstant(
                            Instant.ofEpochSecond(Long.valueOf(rs.getString(COL_INITIAL_FINISH_DATE))),
                            ZoneOffset.UTC
                        )
                    )
                );

                if (records.size() > 0) {
                    lastProcessedRecord = records.get(0);

                    LOGGER.info(
                        "Starting pull process at survey {} with timestamp {}",
                        lastProcessedRecord.getSurveyId(),
                        lastProcessedRecord.getInitialFinishDate()
                    );
                } else {
                    LOGGER.info("No record in persistent storage found as initial starting point");
                }
            }

            return Optional.ofNullable(lastProcessedRecord);
        } finally {
            lastProcessedRecordLock.readLock().unlock();
        }
    }

}
