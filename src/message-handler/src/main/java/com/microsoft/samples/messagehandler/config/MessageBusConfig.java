package com.microsoft.samples.messagehandler.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageBusConfig {
    private String connectionString;
    private String topicName;
    private String subscriptionName;
}
