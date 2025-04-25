package com.example.bank_cards.service;

import com.example.bank_cards.dto.BlockCardRequestDto;
import com.example.bank_cards.enums.RequestStatus;
import com.example.bank_cards.enums.RequestType;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.model.CardRequest;
import com.example.bank_cards.repository.CardRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Log4j2
public class CardRequestService {

    private final CardRequestRepository cardRequestRepository;
    private final UserService userService;
    private final CardService cardService;

    @Transactional()
    public void crateRequestToCreateCard(String email){
        AppUser user = userService.getUserByEmail(email);
        CardRequest cardRequest = new CardRequest();
        cardRequest.setType(RequestType.CREATE_CARD);
        cardRequest.setOwner(user);
        cardRequest.setStatus(RequestStatus.PENDING);
        cardRequestRepository.save(cardRequest);
    }
    @Transactional()
    public void createRequestToBlockCard(String email, BlockCardRequestDto blockCardRequestDto){
        if (!StringUtils.hasText(blockCardRequestDto.getCardNumber())) {
            log.warn("Request failed: card number is blank.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "card number cannot be null or empty"
            );
        }
        AppUser user = userService.getUserByEmail(email);
        Card card = cardService.findCardByNumber(blockCardRequestDto.getCardNumber());
        if (!Objects.equals(card.getOwner().getId(), user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to block this card."
            );
        }

        CardRequest cardRequest = new CardRequest();
        cardRequest.setCardId(card.getId());
        cardRequest.setType(RequestType.BLOCK_CARD);
        cardRequest.setOwner(user);
        cardRequest.setStatus(RequestStatus.PENDING);
        cardRequestRepository.save(cardRequest);
    }
}
