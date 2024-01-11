package com.microsoft.samples.ratelimitingapi.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EndpointConfig {
    private int capacity;
    private int duration;
}
