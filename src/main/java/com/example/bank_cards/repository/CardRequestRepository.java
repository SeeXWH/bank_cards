package com.example.bank_cards.repository;

import com.example.bank_cards.model.CardRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface CardRequestRepository extends JpaRepository<CardRequest, UUID>, JpaSpecificationExecutor<CardRequest> {

}
