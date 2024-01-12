package com.microsoft.samples.messagehandler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.microsoft.samples.messagehandler.config.AppConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RedisClientBuilder {
    private final AppConfig appConfig;

    public RedisClientBuilder(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {

        log.info("Building Redis connection factory");

        var redisConfiguration = new RedisStandaloneConfiguration();
        redisConfiguration.setHostName(appConfig.getRedis().getHost());
        redisConfiguration.setPort(Integer.parseInt(appConfig.getRedis().getPort()));
        redisConfiguration.setPassword(appConfig.getRedis().getPassword());

        return new LettuceConnectionFactory(redisConfiguration);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        log.info("Building Redis template");

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<Object>(Object.class));
        return template;
    }
}
