package com.medallia.references.feedbackdatasync.sqlite;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import static com.medallia.references.feedbackdatasync.constants.DbConstants.COL_INITIAL_FINISH_DATE;
import static com.medallia.references.feedbackdatasync.constants.DbConstants.COL_SURVEYID;
import static com.medallia.references.feedbackdatasync.constants.DbConstants.TABLE_RECORDS;

/**
 * Initialize the SQLite database.
 */
@Configuration
public class DbInitializeConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbInitializeConfig.class);

    @Autowired
    private DataSource dataSource;

    /**
     * Creates the table structure needed in the SQLite database.
     */
    @PostConstruct
    public void initialize() {
        try {
            final Connection connection = dataSource.getConnection();
            final Statement statement = connection.createStatement();

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + TABLE_RECORDS + " (" +
                "  " + COL_SURVEYID + " INTEGER Primary key, " +
                "  " + COL_INITIAL_FINISH_DATE + " INTEGER not null" +
                ")"
            );

            statement.close();
            connection.close();
        } catch (SQLException e) {
            LOGGER.error("Unable to initialize database", e);
            throw new RuntimeException(e);
        }
    }

}
