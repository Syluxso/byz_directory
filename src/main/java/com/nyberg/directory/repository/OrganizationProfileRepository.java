package com.nyberg.directory.repository;

import com.nyberg.directory.domain.OrganizationProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationProfileRepository extends JpaRepository<OrganizationProfile, UUID> {}
