package com.invest.management.alor;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AlorConfig {

    @Bean
    public RestTemplate alorRestTemplate(RestTemplateBuilder builder) {
        return builder
                .additionalInterceptors(alorApiInterceptor())
                .build();
    }

    private ClientHttpRequestInterceptor alorApiInterceptor() {
        return (request, body, execution) -> {
            // Устанавливаем Accept для всех запросов
            request.getHeaders().set("Accept", MediaType.APPLICATION_JSON_VALUE);
            // Content-Type устанавливаем только если есть тело запроса
            // Для запросов с query параметрами и пустым телом Content-Type не нужен
            if (body != null && body.length > 0) {
                request.getHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            }
            return execution.execute(request, body);
        };
    }
}

