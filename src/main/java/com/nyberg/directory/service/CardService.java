package com.nyberg.directory.service;

import com.nyberg.directory.domain.AddressCard;
import com.nyberg.directory.domain.ContactCard;
import com.nyberg.directory.dto.DirectoryDtos.*;
import com.nyberg.directory.repository.AddressCardRepository;
import com.nyberg.directory.repository.ContactCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardService {

    private final ContactCardRepository contactCards;
    private final AddressCardRepository addressCards;

    public ContactCard createOrUpdateContact(UUID organizationId, UUID existingId, ContactCardRequest req) {
        if (req == null) return null;
        ContactCard card = existingId != null
                ? contactCards.findById(existingId).orElseGet(ContactCard::new)
                : new ContactCard();
        if (card.getId() != null && !organizationId.equals(card.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contact card org mismatch");
        }
        card.setOrganizationId(organizationId);
        card.setEmail(blankToNull(req.email()));
        card.setPhone(blankToNull(req.phone()));
        card.setWebsite(blankToNull(req.website()));
        card.setLabel(blankToNull(req.label()));
        return contactCards.save(card);
    }

    public AddressCard createOrUpdateAddress(UUID organizationId, UUID existingId, AddressCardRequest req) {
        if (req == null) return null;
        AddressCard card = existingId != null
                ? addressCards.findById(existingId).orElseGet(AddressCard::new)
                : new AddressCard();
        if (card.getId() != null && !organizationId.equals(card.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Address card org mismatch");
        }
        card.setOrganizationId(organizationId);
        card.setLine1(blankToNull(req.line1()));
        card.setLine2(blankToNull(req.line2()));
        card.setCity(blankToNull(req.city()));
        card.setRegion(blankToNull(req.region()));
        card.setPostalCode(blankToNull(req.postalCode()));
        card.setCountry(blankToNull(req.country()));
        card.setLatitude(req.latitude());
        card.setLongitude(req.longitude());
        card.setLabel(blankToNull(req.label()));
        return addressCards.save(card);
    }

    public ContactCardResponse toContactResponse(UUID id) {
        if (id == null) return null;
        return contactCards.findById(id).map(this::toContactResponse).orElse(null);
    }

    public AddressCardResponse toAddressResponse(UUID id) {
        if (id == null) return null;
        return addressCards.findById(id).map(this::toAddressResponse).orElse(null);
    }

    public ContactCardResponse toContactResponse(ContactCard c) {
        return new ContactCardResponse(c.getId(), c.getOrganizationId(), c.getEmail(), c.getPhone(), c.getWebsite(), c.getLabel());
    }

    public AddressCardResponse toAddressResponse(AddressCard a) {
        return new AddressCardResponse(
                a.getId(), a.getOrganizationId(), a.getLine1(), a.getLine2(), a.getCity(), a.getRegion(),
                a.getPostalCode(), a.getCountry(), a.getLatitude(), a.getLongitude(), a.getLabel());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
