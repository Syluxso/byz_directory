package com.nyberg.directory.repository;

import com.nyberg.directory.domain.ContactCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContactCardRepository extends JpaRepository<ContactCard, UUID> {}
