package com.nyberg.directory.intel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * MaxMind GeoIP Insights web service. Credentials stay on Directory.
 */
@Component
public class MaxMindInsightsClient {

    private static final Logger log = LoggerFactory.getLogger(MaxMindInsightsClient.class);

    private final RestClient http;
    private final String accountId;
    private final String licenseKey;

    public MaxMindInsightsClient(
            @Value("${byz.maxmind.base-url:https://geoip.maxmind.com}") String baseUrl,
            @Value("${byz.maxmind.account-id:}") String accountId,
            @Value("${byz.maxmind.license-key:}") String licenseKey
    ) {
        this.accountId = accountId == null ? "" : accountId.trim();
        this.licenseKey = licenseKey == null ? "" : licenseKey.trim();
        this.http = RestClient.builder().baseUrl(baseUrl.replaceAll("/$", "")).build();
    }

    public boolean configured() {
        return !accountId.isEmpty() && !licenseKey.isEmpty();
    }

    /**
     * @return raw Insights JSON body
     */
    public String lookup(String ip) {
        if (!configured()) {
            throw new MaxMindPermanentException("MaxMind credentials not configured");
        }
        try {
            return http.get()
                    .uri("/geoip/v2.1/insights/{ip}", ip)
                    .headers(h -> h.setBasicAuth(accountId, licenseKey))
                    .retrieve()
                    .onStatus(s -> s.value() == 429 || s.is5xxServerError(), (req, res) -> {
                        throw new MaxMindTransientException("MaxMind Insights HTTP " + res.getStatusCode().value());
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new MaxMindPermanentException("MaxMind Insights HTTP " + res.getStatusCode().value());
                    })
                    .body(String.class);
        } catch (MaxMindTransientException | MaxMindPermanentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("MaxMind Insights transport failure ip={}: {}", ip, e.toString());
            throw new MaxMindTransientException("MaxMind Insights transport failure", e);
        }
    }
}
