package com.nyberg.directory.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "device_ip_intel", schema = "directory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceIpIntel {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR = "error";
    public static final String SOURCE_MAXMIND_INSIGHTS = "maxmind_insights";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(nullable = false, length = 64)
    private String ip;

    private String country;

    @Column(name = "country_iso", length = 2)
    private String countryIso;

    private String continent;

    @Column(name = "continent_code", length = 2)
    private String continentCode;

    private String region;
    private String city;

    @Column(name = "postal_code", length = 16)
    private String postalCode;

    private Double latitude;
    private Double longitude;

    @Column(name = "accuracy_radius_km")
    private Integer accuracyRadiusKm;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    private Integer asn;

    @Column(name = "as_org")
    private String asOrg;

    private String isp;
    private String organization;

    @Column(name = "connection_type", length = 64)
    private String connectionType;

    @Column(name = "user_type", length = 64)
    private String userType;

    @Column(name = "static_ip_score")
    private Double staticIpScore;

    @Column(name = "user_count")
    private Integer userCount;

    @Column(name = "is_anonymous")
    private Boolean anonymous;

    @Column(name = "is_anonymous_vpn")
    private Boolean anonymousVpn;

    @Column(name = "is_hosting")
    private Boolean hosting;

    @Column(name = "is_public_proxy")
    private Boolean publicProxy;

    @Column(name = "is_tor")
    private Boolean tor;

    @Column(name = "is_residential_proxy")
    private Boolean residentialProxy;

    @Column(name = "mobile_country_code", length = 8)
    private String mobileCountryCode;

    @Column(name = "mobile_network_code", length = 8)
    private String mobileNetworkCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json")
    private Map<String, Object> rawJson;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "looked_up_at")
    private Instant lookedUpAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    public boolean isError() {
        return STATUS_ERROR.equals(status);
    }

    public void copyIntelFrom(DeviceIpIntel other) {
        this.country = other.country;
        this.countryIso = other.countryIso;
        this.continent = other.continent;
        this.continentCode = other.continentCode;
        this.region = other.region;
        this.city = other.city;
        this.postalCode = other.postalCode;
        this.latitude = other.latitude;
        this.longitude = other.longitude;
        this.accuracyRadiusKm = other.accuracyRadiusKm;
        this.timeZone = other.timeZone;
        this.asn = other.asn;
        this.asOrg = other.asOrg;
        this.isp = other.isp;
        this.organization = other.organization;
        this.connectionType = other.connectionType;
        this.userType = other.userType;
        this.staticIpScore = other.staticIpScore;
        this.userCount = other.userCount;
        this.anonymous = other.anonymous;
        this.anonymousVpn = other.anonymousVpn;
        this.hosting = other.hosting;
        this.publicProxy = other.publicProxy;
        this.tor = other.tor;
        this.residentialProxy = other.residentialProxy;
        this.mobileCountryCode = other.mobileCountryCode;
        this.mobileNetworkCode = other.mobileNetworkCode;
        this.rawJson = other.rawJson;
        this.source = other.source;
        this.status = other.status;
        this.lookedUpAt = other.lookedUpAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (firstSeenAt == null) firstSeenAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
        if (source == null) source = SOURCE_MAXMIND_INSIGHTS;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
