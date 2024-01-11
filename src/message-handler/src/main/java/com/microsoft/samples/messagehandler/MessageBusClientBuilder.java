package com.microsoft.samples.messagehandler;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSessionReceiverClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.microsoft.samples.messagehandler.config.AppConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageBusClientBuilder {

    private final AppConfig appConfig;

    public MessageBusClientBuilder(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public ServiceBusSessionReceiverClient buildTopicReceiverClient() {
        String subscription = appConfig.getServiceBus().getSubscriptionName();
        String topic = appConfig.getServiceBus().getTopicName();

        log.info("Topic: {}", topic);
        log.info("Subscription: {}", subscription);

        return new ServiceBusClientBuilder()
                .connectionString(appConfig.getServiceBus().getConnectionString())
                .sessionReceiver()
                .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
                .topicName(topic)
                .subscriptionName(subscription)
                .disableAutoComplete() // Make sure to explicitly opt in to manual settlement (e.g. complete, abandon).
                .buildClient();
    }
}
