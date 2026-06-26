package com.b2b.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient productsWebClient(AppProperties properties, WebClient.Builder builder) {
        String baseUrl = Objects.requireNonNull(properties.clients().productsBaseUrl(), "products base URL is required");
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    public WebClient clientsWebClient(AppProperties properties, WebClient.Builder builder) {
        String baseUrl = Objects.requireNonNull(properties.clients().clientsBaseUrl(), "clients base URL is required");
        return builder.baseUrl(baseUrl).build();
    }
}
