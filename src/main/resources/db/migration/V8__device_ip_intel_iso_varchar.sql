-- Hibernate ddl-auto=validate maps String length=2 to varchar, not char.
ALTER TABLE directory.device_ip_intel
    ALTER COLUMN country_iso TYPE VARCHAR(2),
    ALTER COLUMN continent_code TYPE VARCHAR(2);
