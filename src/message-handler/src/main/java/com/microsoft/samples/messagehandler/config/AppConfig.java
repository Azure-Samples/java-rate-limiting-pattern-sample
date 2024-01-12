package com.microsoft.samples.messagehandler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties
@Getter
@Setter
public class AppConfig {

    private MessageBusConfig serviceBus;

    private String rateLimitingServiceUrl;

    private RedisConfig redis;
}
