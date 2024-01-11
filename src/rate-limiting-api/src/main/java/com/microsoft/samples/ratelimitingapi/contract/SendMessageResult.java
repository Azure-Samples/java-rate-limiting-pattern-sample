package com.microsoft.samples.ratelimitingapi.contract;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SendMessageResult {
    String message;
    boolean success;
    String bucketCapacity;
}
