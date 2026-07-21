package com.nyberg.directory.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuidedTaskServiceNormalizeTest {

    @Test
    void normalizesKnownStatuses() {
        assertEquals("open", GuidedTaskService.normalizeStatus("Open"));
        assertEquals("completed", GuidedTaskService.normalizeStatus("COMPLETED"));
        assertEquals("dismissed", GuidedTaskService.normalizeStatus("dismissed"));
        assertEquals("hamlet.waiting", GuidedTaskService.normalizeStatus("Hamlet.Waiting"));
    }

    @Test
    void rejectsBlankOrWeirdStatus() {
        assertThrows(ResponseStatusException.class, () -> GuidedTaskService.normalizeStatus(" "));
        assertThrows(ResponseStatusException.class, () -> GuidedTaskService.normalizeStatus("bad status"));
    }

    @Test
    void priorityDefaults() {
        assertEquals("normal", GuidedTaskService.normalizePriority(null));
        assertEquals("high", GuidedTaskService.normalizePriority("HIGH"));
        assertEquals("normal", GuidedTaskService.normalizePriority("nope"));
    }
}
