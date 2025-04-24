package com.example.bank_cards.model;

import com.example.bank_cards.enums.CardStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Card {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String cardNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    private CardStatus status;

    private BigDecimal balance;

    @JsonIgnore
    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL)
    private List<Transaction> transactions = new ArrayList<>();

    @Embedded
    private CardLimit cardLimit;


}
