package com.nyberg.directory.intel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyberg.directory.domain.DeviceIpIntel;

import java.time.Instant;
import java.util.Map;

/** Projects a MaxMind Insights JSON body onto {@link DeviceIpIntel} columns. */
public final class InsightsMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private InsightsMapper() {}

    public static void applySuccess(DeviceIpIntel row, String json, ObjectMapper mapper) throws Exception {
        JsonNode root = mapper.readTree(json);
        Map<String, Object> raw = mapper.convertValue(root, MAP);
        applySuccess(row, root, raw);
    }

    public static void applySuccess(DeviceIpIntel row, JsonNode root, Map<String, Object> raw) {
        JsonNode country = root.path("country");
        row.setCountry(enName(country));
        row.setCountryIso(text(country, "iso_code"));

        JsonNode continent = root.path("continent");
        row.setContinent(enName(continent));
        row.setContinentCode(text(continent, "code"));

        JsonNode city = root.path("city");
        row.setCity(enName(city));

        JsonNode subdivisions = root.path("subdivisions");
        if (subdivisions.isArray() && !subdivisions.isEmpty()) {
            row.setRegion(enName(subdivisions.get(0)));
        } else {
            row.setRegion(null);
        }

        row.setPostalCode(text(root.path("postal"), "code"));

        JsonNode location = root.path("location");
        row.setLatitude(dbl(location, "latitude"));
        row.setLongitude(dbl(location, "longitude"));
        row.setAccuracyRadiusKm(integer(location, "accuracy_radius"));
        row.setTimeZone(text(location, "time_zone"));

        JsonNode traits = root.path("traits");
        row.setAsn(integer(traits, "autonomous_system_number"));
        row.setAsOrg(text(traits, "autonomous_system_organization"));
        row.setIsp(text(traits, "isp"));
        row.setOrganization(text(traits, "organization"));
        row.setConnectionType(text(traits, "connection_type"));
        row.setUserType(text(traits, "user_type"));
        row.setStaticIpScore(dbl(traits, "static_ip_score"));
        row.setUserCount(integer(traits, "user_count"));
        row.setAnonymous(bool(traits, "is_anonymous"));
        row.setAnonymousVpn(bool(traits, "is_anonymous_vpn"));
        row.setHosting(bool(traits, "is_hosting_provider"));
        row.setPublicProxy(bool(traits, "is_public_proxy"));
        row.setTor(bool(traits, "is_tor_exit_node"));
        row.setResidentialProxy(bool(traits, "is_residential_proxy"));
        row.setMobileCountryCode(text(traits, "mobile_country_code"));
        row.setMobileNetworkCode(text(traits, "mobile_network_code"));

        row.setRawJson(raw);
        row.setSource(DeviceIpIntel.SOURCE_MAXMIND_INSIGHTS);
        row.setStatus(DeviceIpIntel.STATUS_SUCCESS);
        row.setLookedUpAt(Instant.now());
    }

    private static String enName(JsonNode node) {
        JsonNode en = node.path("names").path("en");
        return en.isTextual() ? en.asText() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (!v.isTextual()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.intValue() : null;
    }

    private static Double dbl(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.doubleValue() : null;
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isBoolean() ? v.booleanValue() : null;
    }
}
