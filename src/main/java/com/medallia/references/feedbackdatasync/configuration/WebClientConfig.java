package com.medallia.references.feedbackdatasync.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.web.server.UnAuthenticatedServerOAuth2AuthorizedClientRepository;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures the web client used to make the Query API request.
 */
@Configuration
public class WebClientConfig {

    /**
     * Creates the web client used to make the Query API request.
     *
     * @param clientRegistrations the set of OAuth2-enabled client registrations
     * @return the web client instance
     */
    @Bean
    public WebClient webClient(final ReactiveClientRegistrationRepository clientRegistrations) {

        final InMemoryReactiveOAuth2AuthorizedClientService authorizedClientService =
            new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrations);

        final ServerOAuth2AuthorizedClientExchangeFilterFunction oauth =
            new ServerOAuth2AuthorizedClientExchangeFilterFunction(
                new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                    clientRegistrations,
                    authorizedClientService
                )
            );

        oauth.setDefaultClientRegistrationId("medallia");

        return WebClient.builder()
            .filter(oauth)
            .build();
    }

}
