package com.nyberg.directory.repository;

import com.nyberg.directory.domain.AddressCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AddressCardRepository extends JpaRepository<AddressCard, UUID> {}
