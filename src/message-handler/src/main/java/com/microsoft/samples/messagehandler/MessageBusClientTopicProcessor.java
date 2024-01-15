package com.microsoft.samples.messagehandler;

import com.azure.core.util.IterableStream;
import com.azure.messaging.servicebus.*;
import com.microsoft.samples.messagehandler.lock.LockService;

import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

@Slf4j
@Service
/**
 * Implementing SmartLifecycle to start and stop the processor client.
 * https://github.com/Azure/azure-sdk-for-java/issues/29997
 */
public class MessageBusClientTopicProcessor implements SmartLifecycle {

    private static final int MAX_RATE_LIMIT_PER_MINUTE = 300;
    private static final int MAX_MESSAGE_COUNT = 30;
    private final ServiceBusSessionReceiverClient serviceBusSessionReceiverClient;
    private ServiceBusReceiverClient serviceBusReceiverClient;
    private boolean sessionAccepted = false;
    private final ApiClientBuilder apiClientBuilder;
    private boolean running;

    private final String LOCK_PARTITION_1 = "topic-processor-lock-1";
    private final String LOCK_PARTITION_2 = "topic-processor-lock-2";
    private final String LOCK_PARTITION_3 = "topic-processor-lock-3";

    private final int LOCK_DURATION_IN_SECONDS = 30;

    private int rateLimitHits = 0;

    @Autowired
    private LockService lockService;

    public MessageBusClientTopicProcessor(MessageBusClientBuilder messageBusClientBuilder,
            ApiClientBuilder apiClientBuilder, RedisClientBuilder redisClientBuilder) {
        log.info("Creating MessageBusClientTopicProcessor");

        this.apiClientBuilder = apiClientBuilder;
        this.serviceBusSessionReceiverClient = messageBusClientBuilder.buildTopicReceiverClient();
    }

    private void processMessages(IterableStream<ServiceBusReceivedMessage> messages) {
        messages.forEach(message -> {
            // log.info("[TOPIC PROCESSOR: IN] Id #: {}, Sequence #: {}. Session: {},
            // Delivery Count: {}",
            // message.getMessageId(), message.getSequenceNumber(), message.getSessionId(),
            // message.getDeliveryCount());

            try {
                var httpClient = this.apiClientBuilder.buildApiClient();
                var request = this.apiClientBuilder.buildMessageRequest(message.getBody().toString());

                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                var responseBody = response.body();
                var responseStatusCode = response.statusCode();

                if (responseStatusCode == HttpResponseStatus.TOO_MANY_REQUESTS.code()) {

                    // log.info("[TOPIC PROCESSOR: RATE LIMIT] Response: {}", responseBody);
                    // log.info("[TOPIC PROCESSOR: ABANDON] Id #: {}, Sequence #: {}. Session: {},
                    // Delivery Count: {}",
                    // message.getMessageId(), message.getSequenceNumber(), message.getSessionId(),
                    // message.getDeliveryCount());

                    rateLimitHits++;
                    serviceBusReceiverClient.abandon(message);

                    return;
                }

                // log.info("[RATE LIMITING API] Response: {}", responseBody);

                // log.info(
                // "[TOPIC PROCESSOR: DONE] Completing message. Id #: {}, Sequence #: {}.
                // Session: {}, Delivery Count: {}",
                // message.getMessageId(), message.getSequenceNumber(), message.getSessionId(),
                // message.getDeliveryCount());
                serviceBusReceiverClient.complete(message);

            } catch (Exception e) {
                log.error("[TOPIC PROCESSOR: EXCEPTION]: " + e.getMessage());

                serviceBusReceiverClient.abandon(message);
            }
        });

        log.info("[TOPIC PROCESSOR: COMPLETE] Completed processing messages");
        log.info("[TOPIC PROCESSOR: RATE LIMIT] Hits: {}", rateLimitHits);
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

        long startTime = 0;
        var totalMessages = 0;
        var timeStarted = false;

        while (running) {

            // log.info("Topic Processor waiting for session");
            var partitionAcquired = "";

            try {
                if (sessionAccepted) {
                    // log.info("Topic Processor session accepted");

                    // log.info("Acquiring lock");

                    if (timeStarted == false) {
                        startTime = System.nanoTime();
                        timeStarted = true;
                    }

                    var lockPartitions = new String[] { LOCK_PARTITION_1, LOCK_PARTITION_2, LOCK_PARTITION_3 };

                    var lockAcquired = false;

                    while (lockAcquired == false) {
                        for (var lockPartition : lockPartitions) {
                            // log.info("Acquiring lock for partition: {} for {} seconds", lockPartition,
                            // LOCK_DURATION_IN_SECONDS);
                            lockAcquired = lockService.acquire(lockPartition, LOCK_DURATION_IN_SECONDS);
                            if (lockAcquired) {

                                partitionAcquired = lockPartition;
                                // log.info("[TOPIC PROCESSOR: LOCKED] Lock acquired for partition: {} for {}
                                // seconds",
                                // lockPartition,
                                // LOCK_DURATION_IN_SECONDS);
                                break;
                            }

                            // log.info("Lock not acquired for partition: {}", lockPartition);
                            // log.info("Topic Processor waiting for 3 seconds to try another partition");

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

                    // log.info("Topic Processor closed to accept next session");
                    // log.info("Topic Processor releasing lock");

                    // lockService.release(partitionAcquired);

                    // log.info("[TOPIC PROCESSOR: RELEASE] Lock released for partition: {}",
                    // partitionAcquired);

                    totalMessages += messageCount;

                    long endTime = System.nanoTime();
                    long duration = endTime - startTime;
                    long totalTimeInSeconds = duration / 1000000000;

                    double messagesPerSecond = (double) totalMessages / totalTimeInSeconds;
                    double rateConsumed = (double) messagesPerSecond * 60 / MAX_RATE_LIMIT_PER_MINUTE * 100;

                    log.info(
                            "[RATE METER:] Total time passed: {} seconds, {} messages processed, RATE {} messages/second, ({} per minute), Rate defined: {} messages/minute, Rate consumed: {}",
                            totalTimeInSeconds,
                            totalMessages,
                            String.format("%.1f", messagesPerSecond),
                            String.format("%.1f", messagesPerSecond * 60),
                            MAX_RATE_LIMIT_PER_MINUTE,
                            String.format("%.1f", rateConsumed));

                    if (totalTimeInSeconds >= 300) {
                        log.info("[RATE METER:] 5 minutes period exceeded, resetting rate meter");
                        startTime = System.nanoTime();
                        totalMessages = 0;
                    }

                } else {

                    log.info("Topic Processor acquiring next session");

                    serviceBusReceiverClient = serviceBusSessionReceiverClient.acceptNextSession();
                    sessionAccepted = true;
                }

            } catch (Exception e) {
                log.error("Topic Processor Session Accept Exception: " + e.getMessage());

                lockService.release(partitionAcquired);

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
