package com.example.bank_cards.serviceInterface;

import com.example.bank_cards.dto.BlockCardRequestDto;
import com.example.bank_cards.enums.RequestStatus;
import com.example.bank_cards.enums.RequestType;
import com.example.bank_cards.model.CardRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CardRequestServiceImpl {

    void createRequestToCreateCard(String email);

    void createRequestToBlockCard(String email, BlockCardRequestDto blockCardRequestDto);

    CardRequest setRequestStatus(UUID requestId, RequestStatus requestStatus);

    List<CardRequest> getCardRequestsWithFilter(
            String userEmail,
            RequestType typeFilter,
            RequestStatus statusFilter,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);
}