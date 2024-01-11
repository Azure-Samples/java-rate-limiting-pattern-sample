package com.microsoft.samples.messagehandler;

import com.microsoft.samples.messagehandler.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ApiClientBuilder {
    private final AppConfig appConfig;
    private HttpClient httpClient;

    public ApiClientBuilder(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public HttpClient buildApiClient() throws IOException, InterruptedException {

        if (httpClient != null) {
            return httpClient;
        }

        String baseUrl = appConfig.getRateLimitingServiceUrl();
        log.info("Building API Client with base URL: {}", baseUrl);

        httpClient = HttpClient.newHttpClient();

        log.info("Checking if API is available: {}", baseUrl);
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .GET()
                .build();

        var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        log.info("API is available: {}", response);

        return httpClient;
    }

    public HttpRequest buildMessageRequest(String message) {
        String baseUrl = appConfig.getRateLimitingServiceUrl();

        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/messages"))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(message))
                .build();
    }
}
