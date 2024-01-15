package com.microsoft.samples.messagehandler;

import com.azure.core.util.IterableStream;
import com.azure.messaging.servicebus.*;
import com.microsoft.samples.messagehandler.lock.LockService;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
/**
 * Implementing SmartLifecycle to start and stop the processor client.
 * https://github.com/Azure/azure-sdk-for-java/issues/29997
 */
public class MessageBusClientTopicProcessor implements ApplicationListener<ApplicationReadyEvent> {

    private static final int MAX_MESSAGE_COUNT = 30;
    private ServiceBusSessionReceiverClient serviceBusSessionReceiverClient;
    private ServiceBusReceiverClient serviceBusReceiverClient;
    private MessageBusClientBuilder messageBusClientBuilder;
    private boolean sessionAccepted = false;
    private final ApiClientBuilder apiClientBuilder;
    private boolean running;
    private Counter totalMessagesCounter;
    private Counter rateLimitCounter;

    private final String LOCK_PARTITION_1 = "topic-processor-lock-1";
    private final String LOCK_PARTITION_2 = "topic-processor-lock-2";
    private final String LOCK_PARTITION_3 = "topic-processor-lock-3";

    private final int LOCK_DURATION_IN_SECONDS = 30;

    private int rateLimitHits = 0;

    @Autowired
    private LockService lockService;

    public MessageBusClientTopicProcessor(MessageBusClientBuilder messageBusClientBuilder,
            ApiClientBuilder apiClientBuilder, RedisClientBuilder redisClientBuilder,
            CompositeMeterRegistry meterRegistry) {
        log.info("Creating MessageBusClientTopicProcessor");

        this.apiClientBuilder = apiClientBuilder;
        this.messageBusClientBuilder = messageBusClientBuilder;
        this.serviceBusSessionReceiverClient = this.messageBusClientBuilder.buildTopicReceiverClient();

        totalMessagesCounter = meterRegistry.counter("total_messages_processed");
        rateLimitCounter = meterRegistry.counter("rate_limit_hits");
    }

    @Timed(value = "processMessages", description = "Process messages from Service Bus", longTask = true)
    private void processMessages(IterableStream<ServiceBusReceivedMessage> messages) {
        messages.forEach(message -> {
            try {
                var httpClient = this.apiClientBuilder.buildApiClient();
                var request = this.apiClientBuilder.buildMessageRequest(message.getBody().toString());

                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                var responseStatusCode = response.statusCode();

                if (responseStatusCode == HttpResponseStatus.TOO_MANY_REQUESTS.code()) {

                    rateLimitHits++;
                    rateLimitCounter.increment();
                    serviceBusReceiverClient.abandon(message);

                    return;
                }

                serviceBusReceiverClient.complete(message);
                totalMessagesCounter.increment();

            } catch (Exception e) {
                log.error("[TOPIC PROCESSOR: EXCEPTION]: " + e.getMessage());

                serviceBusReceiverClient.abandon(message);
            }
        });

        log.info("[TOPIC PROCESSOR: COMPLETE] Completed processing messages");
        log.info("[TOPIC PROCESSOR: RATE LIMIT] Hits: {}", rateLimitHits);
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        running = true;
        log.info("Application ready event received, application running: {}", running);
    }

    @EventListener
    public void onEvent(AvailabilityChangeEvent<ReadinessState> event) {
        log.info("Availability change event received, application running: {}, state {}", running, event.getState());

        if (event.getState() == ReadinessState.ACCEPTING_TRAFFIC) {
            log.info("Topic Processor can start running");
            runMessageProcessor();
        }
    }

    public void runMessageProcessor() {
        log.info("Topic Processor started, application running: {}", running);

        while (running) {

            log.info("Topic Processor waiting for session");
            var partitionAcquired = "";

            try {
                if (sessionAccepted) {

                    log.info("Topic Processor session accepted");
                    log.info("Acquiring lock");

                    var lockPartitions = new String[] { LOCK_PARTITION_1, LOCK_PARTITION_2, LOCK_PARTITION_3 };

                    var lockAcquired = false;

                    while (lockAcquired == false) {
                        for (var lockPartition : lockPartitions) {
                            lockAcquired = lockService.acquire(lockPartition, LOCK_DURATION_IN_SECONDS);
                            if (lockAcquired) {

                                partitionAcquired = lockPartition;
                                log.info("[TOPIC PROCESSOR: LOCKED] Lock acquired for partition: {} for {} seconds",
                                        lockPartition, LOCK_DURATION_IN_SECONDS);
                                break;
                            }

                            log.info("Topic Processor waiting for 3 seconds to try another partition");

                            try {
                                Thread.sleep(Duration.ofSeconds(3).toMillis());
                            } catch (InterruptedException e) {
                                log.error("Topic Processor Sleep Exception: " + e.getMessage());
                            }
                        }
                    }

                    log.info("Topic Processor waiting for messages");
                    var messages = serviceBusReceiverClient.receiveMessages(MAX_MESSAGE_COUNT);
                    var messageCount = messages.stream().count();
                    log.info("Received {} messages", messageCount);

                    processMessages(messages);

                    serviceBusReceiverClient.close();
                    sessionAccepted = false;

                } else {

                    log.info("Topic Processor acquiring next session");

                    serviceBusReceiverClient = serviceBusSessionReceiverClient.acceptNextSession();
                    sessionAccepted = true;
                }

            } catch (Exception e) {
                log.error("Topic Processor Session Accept Exception: " + e.getMessage());

                lockService.release(partitionAcquired);

                // Usually the exception after the timeout is:
                // The receiver client is terminated. Re-create the client to continue receive
                // attempt.
                serviceBusSessionReceiverClient = messageBusClientBuilder.buildTopicReceiverClient();

                try {
                    log.info("Topic Processor Session Accept Sleep for 3 seconds");
                    Thread.sleep(Duration.ofSeconds(3).toMillis());
                } catch (InterruptedException e1) {
                    log.error("Topic Processor Session Accept Sleep Exception: " + e1.getMessage());
                }
            }
        }
    }
}
