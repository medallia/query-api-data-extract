package com.medallia.references.feedbackdatasync.model;

import java.math.BigInteger;
import java.time.ZonedDateTime;

/**
 * A processed record.
 */
public class ProcessedRecord {

    private BigInteger surveyId;
    private ZonedDateTime initialFinishDate;

    public ProcessedRecord(final BigInteger surveyId, final ZonedDateTime initialFinishDate) {
        this.surveyId = surveyId;
        this.initialFinishDate = initialFinishDate;
    }

    public BigInteger getSurveyId() {
        return this.surveyId;
    }

    public ZonedDateTime getInitialFinishDate() {
        return this.initialFinishDate;
    }

}
