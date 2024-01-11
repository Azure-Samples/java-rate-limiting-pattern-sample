package com.microsoft.samples.messagehandler;

import com.azure.core.util.IterableStream;
import com.azure.messaging.servicebus.*;

import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

@Slf4j
@Service
/**
 * Implementing SmartLifecycle to start and stop the processor client.
 * https://github.com/Azure/azure-sdk-for-java/issues/29997
 */
public class MessageBusClientTopicProcessor implements SmartLifecycle {

    private static final int MAX_MESSAGE_COUNT = 100;
    private final ServiceBusSessionReceiverClient serviceBusSessionReceiverClient;
    private ServiceBusReceiverClient serviceBusReceiverClient;
    private boolean sessionAccepted = false;
    private final ApiClientBuilder apiClientBuilder;
    private boolean running;

    public MessageBusClientTopicProcessor(MessageBusClientBuilder messageBusClientBuilder,
            ApiClientBuilder apiClientBuilder) {
        log.info("Creating MessageBusClientTopicProcessor");

        this.apiClientBuilder = apiClientBuilder;
        this.serviceBusSessionReceiverClient = messageBusClientBuilder.buildTopicReceiverClient();
    }

    private void processMessages(IterableStream<ServiceBusReceivedMessage> messages) {
        messages.forEach(message -> {
            log.info("[TOPIC PROCESSOR: IN] Id #: {}, Sequence #: {}. Session: {}, Delivery Count: {}",
                    message.getMessageId(), message.getSequenceNumber(), message.getSessionId(),
                    message.getDeliveryCount());

            try {
                var httpClient = this.apiClientBuilder.buildApiClient();
                var request = this.apiClientBuilder.buildMessageRequest(message.getBody().toString());

                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                var responseBody = response.body();
                var responseStatusCode = response.statusCode();

                if (responseStatusCode == HttpResponseStatus.TOO_MANY_REQUESTS.code()) {

                    log.info("[TOPIC PROCESSOR: RATE LIMIT] Response: {}", responseBody);
                    log.info("[TOPIC PROCESSOR: ABANDON] Id #: {}, Sequence #: {}. Session: {}, Delivery Count: {}",
                            message.getMessageId(), message.getSequenceNumber(), message.getSessionId(),
                            message.getDeliveryCount());

                    serviceBusReceiverClient.abandon(message);

                    return;
                }

                log.info("[RATE LIMITING API] Response: {}", responseBody);

                log.info(
                        "[TOPIC PROCESSOR: DONE] Completing message. Id #: {}, Sequence #: {}. Session: {}, Delivery Count: {}",
                        message.getMessageId(), message.getSequenceNumber(), message.getSessionId(),
                        message.getDeliveryCount());
                serviceBusReceiverClient.complete(message);

            } catch (Exception e) {
                log.error("[TOPIC PROCESSOR: EXCEPTION]: " + e.getMessage());

                serviceBusReceiverClient.abandon(message);
            }
        });
    }

    @PostConstruct
    public void startProcessor() {

    }

    @PreDestroy
    public void stopProcessor() {

    }

    @Override
    public void start() {
        log.info("Topic Processor started");
        running = true;

        while (running) {

            log.info("Topic Processor waiting for session");

            try {
                if (sessionAccepted) {
                    log.info("Topic Processor session accepted");

                    log.info("Topic Processor waiting for messages");
                    var messages = serviceBusReceiverClient.receiveMessages(MAX_MESSAGE_COUNT);
                    log.info("Received {} messages", messages.stream().count());
                    processMessages(messages);

                    log.info("Topic Processor closing to accept next session");

                    serviceBusReceiverClient.close();
                    sessionAccepted = false;
                } else {

                    log.info("Topic Processor acquiring next session");

                    serviceBusReceiverClient = serviceBusSessionReceiverClient.acceptNextSession();
                    sessionAccepted = true;
                }

            } catch (Exception e) {
                log.error("Topic Processor Session Accept Exception: " + e.getMessage());

                try {
                    log.info("Topic Processor Session Accept Sleep for 3 seconds");
                    Thread.sleep(Duration.ofSeconds(3).toMillis());
                } catch (InterruptedException e1) {
                    log.error("Topic Processor Session Accept Sleep Exception: " + e1.getMessage());
                }
            }
        }
    }

    @Override
    public void stop() {
        log.info("Topic Processor closed");
        serviceBusSessionReceiverClient.close();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
