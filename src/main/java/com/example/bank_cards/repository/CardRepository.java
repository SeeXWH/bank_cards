package com.example.bank_cards.repository;


import com.example.bank_cards.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID>, JpaSpecificationExecutor<Card> {

    boolean existsByCardNumber(String cardNumber);

     Optional<Card> findByCardNumber(String cardNumber);

     List<Card> findAllByOwnerId(UUID ownerId);

}
