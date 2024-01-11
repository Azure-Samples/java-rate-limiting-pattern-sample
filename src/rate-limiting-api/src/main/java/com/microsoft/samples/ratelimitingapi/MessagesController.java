package com.microsoft.samples.ratelimitingapi;

import com.microsoft.samples.ratelimitingapi.contract.SendMessageRequest;
import com.microsoft.samples.ratelimitingapi.contract.SendMessageResult;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
public class MessagesController {
    private final Bucket rateLimiter;

    public MessagesController(Bucket rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/messages")
    public ResponseEntity<SendMessageResult> sendMessage(@RequestBody SendMessageRequest request) {

        String replicaName = System.getenv("HOSTNAME");
        log.info("Received message: {} ", request.getMessage());

        try {
            if (rateLimiter.tryConsume(1)) {
                log.info("Message Processed, rate limit consumed: 1, Served by replica: " + replicaName);
            } else {
                var limitExceeded = SendMessageResult.builder().message("Rate limit exceeded.").success(false).build();
                log.error("Rate limit exceeded.");

                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(limitExceeded);
            }
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
            var error = SendMessageResult.builder()
                    .message(e.getMessage())
                    .success(false)
                    .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        var result = SendMessageResult.builder()
                .message(request.getMessage())
                .bucketCapacity(
                        rateLimiter.getAvailableTokens() + " tokens remaining, Served by replica: " + replicaName)
                .success(true).build();

        log.info("[OK] Message processed successfully with result: {}", result);

        return ResponseEntity.ok(result);
    }
}
