package com.microsoft.samples.ratelimitingapi;

import com.microsoft.samples.ratelimitingapi.contract.HomeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<HomeResponse> home() {
        String replicaName = System.getenv("HOSTNAME");
        log.info("Home endpoint called by replica: " + replicaName);
        var response = HomeResponse.builder()
                .message("Welcome to the Rate Limiting API. Served by replica: " + replicaName)
                .build();
        return ResponseEntity.ok(response);
    }
}
