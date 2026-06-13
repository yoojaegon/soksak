package com.soksak.soksak.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient aiServerRestClient(
            @Value("${ai-server.base-url}") String baseUrl,
            @Value("${ai-server.connect-timeout}") int connectTimeout,
            @Value("${ai-server.read-timeout}") int readTimeout
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
