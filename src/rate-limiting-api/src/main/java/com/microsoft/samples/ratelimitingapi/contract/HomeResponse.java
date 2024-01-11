package com.microsoft.samples.ratelimitingapi.contract;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Jacksonized
@Builder
public class HomeResponse {
    String message;
}

