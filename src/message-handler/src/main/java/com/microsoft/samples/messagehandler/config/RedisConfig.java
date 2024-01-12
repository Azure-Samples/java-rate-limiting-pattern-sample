package com.microsoft.samples.messagehandler.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RedisConfig {
    private String host;
    private String port;
    private String password;
}