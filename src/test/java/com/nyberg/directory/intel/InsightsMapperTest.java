package com.nyberg.directory.intel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyberg.directory.domain.DeviceIpIntel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightsMapperTest {

    private static final String SAMPLE = """
            {
              "city": { "names": { "en": "Minneapolis" } },
              "continent": { "code": "NA", "names": { "en": "North America" } },
              "country": { "iso_code": "US", "names": { "en": "United States" } },
              "location": {
                "accuracy_radius": 20,
                "latitude": 44.98,
                "longitude": -93.26,
                "time_zone": "America/Chicago"
              },
              "postal": { "code": "55401" },
              "subdivisions": [ { "iso_code": "MN", "names": { "en": "Minnesota" } } ],
              "traits": {
                "autonomous_system_number": 7922,
                "autonomous_system_organization": "Comcast Cable Communications, LLC",
                "isp": "Comcast Cable",
                "organization": "Comcast Cable",
                "connection_type": "Cable/DSL",
                "user_type": "residential",
                "static_ip_score": 0.34,
                "user_count": 2,
                "is_anonymous": false,
                "is_anonymous_vpn": false,
                "is_hosting_provider": false,
                "is_public_proxy": false,
                "is_tor_exit_node": false,
                "is_residential_proxy": false
              }
            }
            """;

    @Test
    void projectsInsightsColumns() throws Exception {
        DeviceIpIntel row = new DeviceIpIntel();
        InsightsMapper.applySuccess(row, SAMPLE, new ObjectMapper());
        assertEquals("success", row.getStatus());
        assertEquals("United States", row.getCountry());
        assertEquals("US", row.getCountryIso());
        assertEquals("NA", row.getContinentCode());
        assertEquals("Minneapolis", row.getCity());
        assertEquals("Minnesota", row.getRegion());
        assertEquals("55401", row.getPostalCode());
        assertEquals(44.98, row.getLatitude(), 0.001);
        assertEquals(20, row.getAccuracyRadiusKm());
        assertEquals("America/Chicago", row.getTimeZone());
        assertEquals(7922, row.getAsn());
        assertEquals("Comcast Cable Communications, LLC", row.getAsOrg());
        assertEquals("residential", row.getUserType());
        assertEquals(Boolean.FALSE, row.getAnonymousVpn());
        assertTrue(row.getRawJson().containsKey("traits"));
    }
}
