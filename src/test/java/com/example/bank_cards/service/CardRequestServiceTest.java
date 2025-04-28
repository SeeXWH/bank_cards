package com.example.bank_cards.service;

import com.example.bank_cards.dto.BlockCardRequestDto;
import com.example.bank_cards.enums.RequestStatus;
import com.example.bank_cards.enums.RequestType;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.model.CardRequest;
import com.example.bank_cards.repository.CardRequestRepository;
import com.example.bank_cards.serviceInterface.CardServiceImpl;
import com.example.bank_cards.serviceInterface.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardRequestServiceTest {

    @Mock
    private CardRequestRepository cardRequestRepository;

    @Mock
    private UserServiceImpl userService;

    @Mock
    private CardServiceImpl cardService;

    @InjectMocks
    private CardRequestService cardRequestService;

    @Test
    void createRequestToCreateCard_UserNotFound_ThrowsException() {
        String email = "nonexistent@test.com";
        when(userService.getUserByEmail(email)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND)
        );

        assertThrows(ResponseStatusException.class,
                () -> cardRequestService.createRequestToCreateCard(email));
    }

    @Test
    void createRequestToCreateCard_ValidUser_CreatesRequest() {
        AppUser user = new AppUser();
        user.setEmail("test@user.com");
        when(userService.getUserByEmail(user.getEmail())).thenReturn(user);

        cardRequestService.createRequestToCreateCard(user.getEmail());

        verify(cardRequestRepository).save(argThat(request ->
                request.getType() == RequestType.CREATE_CARD &&
                        request.getStatus() == RequestStatus.PENDING &&
                        request.getOwner().equals(user)
        ));
    }

    @Test
    void createRequestToBlockCard_CardNotOwned_ThrowsForbidden() {
        String email = "owner@test.com";
        String cardNumber = "4111111111111111";
        BlockCardRequestDto dto = new BlockCardRequestDto(cardNumber);

        AppUser user = new AppUser();
        user.setId(UUID.fromString("2ea79975-e726-4743-8031-80c773b9bfc6"));
        Card card = new Card();
        card.setOwner(new AppUser(UUID.fromString("2ea79975-e726-4743-8031-80c773b9bfc7")));

        when(userService.getUserByEmail(email)).thenReturn(user);
        when(cardService.findCardByNumber(cardNumber)).thenReturn(card);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> cardRequestService.createRequestToBlockCard(email, dto));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void setRequestStatus_RequestNotFound_ThrowsException() {
        UUID requestId = UUID.randomUUID();
        when(cardRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> cardRequestService.setRequestStatus(requestId, RequestStatus.APPROVED));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getCardRequestsWithFilter_DateRange_AppliesSpecification() {
        
        Pageable pageable = Pageable.unpaged();
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        when(cardRequestRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(new CardRequest())));

        
        List<CardRequest> result = cardRequestService.getCardRequestsWithFilter(
                null, null, null, from, to, pageable
        );

        
        assertFalse(result.isEmpty());
        verify(cardRequestRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void getCardRequestsWithFilter_UserEmail_ValidatesUser() {
        String email = "test@user.com";
        Pageable pageable = Pageable.unpaged();

        when(userService.getUserByEmail(email)).thenReturn(new AppUser());
        when(cardRequestRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(Page.empty());

        List<CardRequest> result = cardRequestService.getCardRequestsWithFilter(
                email, null, null, null, null, pageable
        );

        assertTrue(result.isEmpty());
        verify(userService).getUserByEmail(email);
    }

    @Test
    void createRequestToBlockCard_CardNotFound_ThrowsException() {
        String email = "user@test.com";
        String cardNumber = "invalid_number";
        BlockCardRequestDto dto = new BlockCardRequestDto(cardNumber);

        when(userService.getUserByEmail(email)).thenReturn(new AppUser());
        when(cardService.findCardByNumber(cardNumber)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND)
        );

        assertThrows(ResponseStatusException.class,
                () -> cardRequestService.createRequestToBlockCard(email, dto));
    }

    @Test
    void createRequestToBlockCard_ValidRequest_SavesCorrectData() {
        AppUser user = new AppUser(UUID.fromString("2ea79975-e726-4743-8031-80c773b9bfc6"));
        Card card = new Card();
        card.setId(UUID.fromString("2ea79975-e726-4743-8031-80c773b9bfc7"));
        card.setOwner(user);

        when(userService.getUserByEmail(any())).thenReturn(user);
        when(cardService.findCardByNumber(any())).thenReturn(card);

        cardRequestService.createRequestToBlockCard("test@user.com", new BlockCardRequestDto("4111111111111111"));

        verify(cardRequestRepository).save(argThat(request ->
                request.getType() == RequestType.BLOCK_CARD &&
                        request.getCardId().equals(card.getId()) &&
                        request.getStatus() == RequestStatus.PENDING
        ));
    }

    @Test
    void setRequestStatus_ValidTransition_UpdatesStatus() {
        UUID requestId = UUID.randomUUID();
        CardRequest request = new CardRequest();
        request.setStatus(RequestStatus.PENDING);
        when(cardRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(cardRequestRepository.save(request)).thenReturn(request);
        CardRequest updated = cardRequestService.setRequestStatus(requestId, RequestStatus.APPROVED);
        assertNotNull(updated); 
        assertEquals(RequestStatus.APPROVED, updated.getStatus());
        verify(cardRequestRepository).save(request);
    }
}