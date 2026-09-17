package com.nyberg.directory.intel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublicIpsTest {

    @Test
    void publicV4() {
        assertEquals("8.8.8.8", PublicIps.normalizeOrNull("8.8.8.8"));
    }

    @Test
    void privateRejected() {
        assertNull(PublicIps.normalizeOrNull("127.0.0.1"));
        assertNull(PublicIps.normalizeOrNull("10.0.0.1"));
        assertNull(PublicIps.normalizeOrNull("192.168.1.10"));
        assertNull(PublicIps.normalizeOrNull("100.64.1.1"));
        assertNull(PublicIps.normalizeOrNull("fc00::1"));
        assertNull(PublicIps.normalizeOrNull("not-an-ip"));
    }

    @Test
    void ipv4Mapped() {
        assertEquals("1.1.1.1", PublicIps.normalizeOrNull("::ffff:1.1.1.1"));
        assertNull(PublicIps.normalizeOrNull("::ffff:10.0.0.1"));
    }
}
