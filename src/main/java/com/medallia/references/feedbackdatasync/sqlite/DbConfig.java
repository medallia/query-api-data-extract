package com.medallia.references.feedbackdatasync.sqlite;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Initialize the SQLite database.
 */
@Configuration
public class DbConfig {

    /**
     * Creates the DataSource to the SQLite database.
     *
     * @param url the DataSource URL
     * @return the instantiated DataSource
     */
    @Bean
	public DataSource dataSource(
            @Value("${spring.datasource.url}") final String url
    ) {
        final DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.driverClassName("org.sqlite.JDBC");
        dataSourceBuilder.url(url);

        return dataSourceBuilder.build();
	}

}
