package com.invest.management.moex.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

@Component
public class MoexApiClient {

    private static final Logger log = LoggerFactory.getLogger(MoexApiClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1_000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${moex.api.base-url:https://iss.moex.com/iss}")
    private String baseUrl;

    public MoexApiClient(RestTemplate moexRestTemplate, ObjectMapper objectMapper) {
        this.restTemplate = moexRestTemplate;
        this.objectMapper = objectMapper;
    }

    public JsonNode fetch(String path) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

                if (!response.getStatusCode().is2xxSuccessful()) {
                    log.warn("Попытка {}: статус {} при запросе {}", attempt, response.getStatusCode(), url);
                } else if (response.getBody() != null) {
                    byte[] bodyBytes = decodeBody(response);
                    if (bodyBytes == null) {
                        log.warn("Попытка {}: тело ответа {} пусто после декодирования", attempt, url);
                        continue;
                    }
                    Charset charset = resolveCharset(response.getHeaders().getContentType());
                    String body = new String(bodyBytes, charset).trim();
                    if (body.startsWith("<")) {
                        log.warn("Попытка {}: ответ {} выглядит как HTML ({} символов)", attempt, url, body.length());
                    } else {
                        JsonNode node = objectMapper.readTree(body);
                        throttleBetweenCalls();
                        return node;
                    }
                }
            } catch (Exception ex) {
                log.error("Попытка {} запросить {} завершилась ошибкой: {}", attempt, url, ex.getMessage());
            }

            if (attempt < MAX_ATTEMPTS) {
                sleepBeforeRetry(attempt);
            }
        }
        return null;
    }

    private Charset resolveCharset(MediaType mediaType) {
        if (mediaType != null && mediaType.getCharset() != null) {
            return mediaType.getCharset();
        }
        return StandardCharsets.UTF_8;
    }

    private byte[] decodeBody(ResponseEntity<byte[]> response) throws IOException {
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            return null;
        }
        String encoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        if (encoding != null) {
            if (encoding.equalsIgnoreCase("gzip")) {
                try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(body))) {
                    return gis.readAllBytes();
                }
            }
            if (encoding.equalsIgnoreCase("deflate")) {
                try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(body))) {
                    return iis.readAllBytes();
                }
            }
        }
        return body;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void throttleBetweenCalls() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

