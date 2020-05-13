package com.medallia.references.feedbackdatasync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the Spring Boot application.
 */
@EnableRetry
@EnableScheduling
@SpringBootApplication
public class Application {

    /**
     * Starts the Spring Boot application via the Java VM.
     *
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
