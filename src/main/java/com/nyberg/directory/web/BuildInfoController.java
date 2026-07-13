package com.nyberg.directory.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class BuildInfoController {

    @GetMapping("/api/v1/build-info")
    public Map<String, Object> buildInfo() {
        return Map.of(
                "service", "byz-directory",
                "oauth2ResourceServer", true,
                "time", Instant.now().toString()
        );
    }
}
