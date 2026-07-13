package com.nyberg.directory.logging;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LogController {

    private final LogBuffer logBuffer;

    @GetMapping("/api/v1/admin/logs")
    public List<LogBuffer.LogEntry> getLogs(
            @RequestParam(defaultValue = "200") int lines,
            @RequestParam(required = false) String level) {
        return logBuffer.tail(Math.min(lines, 500), level);
    }
}
