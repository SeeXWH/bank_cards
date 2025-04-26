package com.example.bank_cards.model;


import com.example.bank_cards.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "send_card_id")
    private Card sendCard;

    @ManyToOne
    @JoinColumn(name = "receive_card_id")
    private Card receiveCard;

    private LocalDateTime createdAt;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
