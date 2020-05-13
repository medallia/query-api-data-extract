package com.medallia.references.feedbackdatasync.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.web.server.UnAuthenticatedServerOAuth2AuthorizedClientRepository;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configure the retry parameters.
 */
@Configuration
public class RetryConfig {

    /**
     * Configure the retry mechanism based on the configured parameters.  This
     * retry is used to reattempt any failed Query API requests, typically due
     * to network failures.
     *
     * @param maxAttempts the maximum number of retry attempts to make
     * @param backOffPeriod the number of milliseconds to wait between attempts
     * @return the retry policy as an execution wrapper
     */
    @Bean
    public RetryTemplate retryTemplate(
            @Value("${medallia.queryapi.retry.max:2}") final Integer maxAttempts,
            @Value("${medallia.queryapi.retry.back.off.period.msec:2000}") final Long backOffPeriod
    ) {
        final RetryTemplate retryTemplate = new RetryTemplate();

        final FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(backOffPeriod);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        final SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxAttempts);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }

}
