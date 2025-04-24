package com.example.bank_cards.service;

import com.example.bank_cards.dto.BlockCardRequestDto;
import com.example.bank_cards.enums.RequestStatus;
import com.example.bank_cards.enums.RequestType;
import com.example.bank_cards.model.AppUser;
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

@Service
@RequiredArgsConstructor
@Log4j2
public class CardRequestService {

    private final CardRequestRepository cardRequestRepository;
    private final UserService userService;

    @Transactional()
    public void crateRequestToCreateCard(String email){
        try{
            AppUser user = userService.getUserByEmail(email);
            CardRequest cardRequest = new CardRequest();
            cardRequest.setCardNumber(null);
            cardRequest.setType(RequestType.CREATE_CARD);
            cardRequest.setOwner(user);
            cardRequest.setStatus(RequestStatus.PENDING);
            cardRequestRepository.save(cardRequest);
        } catch (ResponseStatusException e) {
            throw e;
        }
        catch (DataAccessException e) {
            log.error("Database error while retrieving user by email: {}", email);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Database error retrieving user.");
        }
        catch (Exception e) {
            log.error("Unexpected error retrieving user by email: {}", email);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error retrieving user.");
        }
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
        //дописать проверку карты на то что она существует
        //допистаь проверку карты что она пренадлежит именно этому пользователю
        //пока забить хуй и сделать сервис для работы с картами
        try{
            AppUser user = userService.getUserByEmail(email);
            CardRequest cardRequest = new CardRequest();
            cardRequest.setCardNumber(blockCardRequestDto.getCardNumber());
            cardRequest.setType(RequestType.BLOCK_CARD);
            cardRequest.setOwner(user);
            cardRequest.setStatus(RequestStatus.PENDING);
            cardRequestRepository.save(cardRequest);
        } catch (ResponseStatusException e) {
            throw e;
        }
        catch (DataAccessException e) {
            log.error("Database error while retrieving user by email: {}", email);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Database error retrieving user.");
        }
        catch (Exception e) {
            log.error("Unexpected error retrieving user by email: {}", email);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error retrieving user.");
        }
    }
}
