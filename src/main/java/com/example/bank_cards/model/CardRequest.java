package com.example.bank_cards.model;

import com.example.bank_cards.enums.RequestStatus;
import com.example.bank_cards.enums.RequestType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    private String cardNumber;

    @Enumerated(EnumType.STRING)
    private RequestType type;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
