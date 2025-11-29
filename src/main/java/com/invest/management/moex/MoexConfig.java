package com.invest.management.moex;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class MoexConfig {

    @Bean
    public RestTemplate moexRestTemplate(RestTemplateBuilder builder) {
        return builder
            .additionalInterceptors(userAgentInterceptor())
            .build();
    }

    private ClientHttpRequestInterceptor userAgentInterceptor() {
        return (request, body, execution) -> {
            request.getHeaders().set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
            request.getHeaders().setAccept(MediaType.parseMediaTypes("application/json,*/*;q=0.8"));
            request.getHeaders().set("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
            request.getHeaders().set("Referer", "https://iss.moex.com/");
            request.getHeaders().set("Connection", "keep-alive");
            request.getHeaders().set("Accept-Encoding", "gzip, deflate");
            return execution.execute(request, body);
        };
    }
}

