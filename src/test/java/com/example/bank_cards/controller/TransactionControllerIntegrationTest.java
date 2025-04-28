package com.example.bank_cards.controller;

import com.example.bank_cards.dto.*;
import com.example.bank_cards.enums.TransactionType;
import com.example.bank_cards.serviceInterface.TransactionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerIntegrationTest {

    private static final String BASE_URL = "/api/transaction";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    private static ObjectMapper objectMapper;

    @MockBean
    private TransactionServiceImpl transactionService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;
    @Captor
    private ArgumentCaptor<UUID> uuidCaptor;
    @Captor
    private ArgumentCaptor<BigDecimal> amountCaptor;
    @Captor
    private ArgumentCaptor<String> emailCaptor;
    @Captor
    private ArgumentCaptor<TransactionFilter> filterCaptor;
    @Captor
    private ArgumentCaptor<TransferRequestDto> transferDtoCaptor;
    @Captor
    private ArgumentCaptor<DebitRequestDto> debitDtoCaptor;
    @Captor
    private ArgumentCaptor<TopUpRequestDto> topUpDtoCaptor;


    private UUID sampleCardId1;
    private UUID sampleCardId2;
    private String sampleUserEmail;
    private String sampleAdminEmail;
    private BigDecimal sampleAmount;
    private TransactionDto sampleTransactionDto;

    @BeforeAll
    static void beforeAll() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @BeforeEach
    void setUp() {
        sampleCardId1 = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        sampleCardId2 = UUID.fromString("987e6543-e21b-32d3-c456-556614174111");
        sampleUserEmail = "user@example.com";
        sampleAdminEmail = "admin@example.com";
        sampleAmount = new BigDecimal("100.50");


        sampleTransactionDto = new TransactionDto();
        sampleTransactionDto.setId(UUID.randomUUID());
        sampleTransactionDto.setType(TransactionType.TRANSFER);
        sampleTransactionDto.setSendCardNumber("**** **** **** 1111");
        sampleTransactionDto.setReceiveCardNumber("**** **** **** 2222");
        sampleTransactionDto.setAmount(sampleAmount);
        sampleTransactionDto.setCreatedAt(LocalDateTime.now().minusHours(1).withNano(0));
    }


    private TransactionDto createSampleTransactionDto(UUID id, TransactionType type, String sender, String receiver, BigDecimal amount) {
        TransactionDto dto = new TransactionDto();
        dto.setId(id);
        dto.setType(type);
        dto.setSendCardNumber(sender);
        dto.setReceiveCardNumber(receiver);
        dto.setAmount(amount);
        dto.setCreatedAt(LocalDateTime.now().withNano(0));
        return dto;
    }


    @Nested
    @DisplayName("POST /api/transaction/transfer-between-cards Tests")
    class TransferBetweenCardsTests {

        @Test
        @WithMockUser(username = "owner@example.com")
        @DisplayName("shouldTransferSuccessfullyWhenUserAuthenticatedAndOwnsCards")
        void shouldTransferSuccessfullyWhenUserAuthenticatedAndOwnsCards() throws Exception {
            TransferRequestDto requestDto = new TransferRequestDto();
            requestDto.setSendCardId(sampleCardId1);
            requestDto.setReceiveCardId(sampleCardId2);
            requestDto.setAmount(sampleAmount);
            doNothing().when(transactionService).transferBetweenCards(
                    eq(sampleCardId1), eq(sampleCardId2), eq(sampleAmount), eq("owner@example.com")
            );
            mockMvc.perform(post(BASE_URL + "/transfer-between-cards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string(isEmptyOrNullString()));
            verify(transactionService, times(1)).transferBetweenCards(
                    uuidCaptor.capture(), uuidCaptor.capture(), amountCaptor.capture(), emailCaptor.capture()
            );
            assertEquals(sampleCardId1, uuidCaptor.getAllValues().get(0));
            assertEquals(sampleCardId2, uuidCaptor.getAllValues().get(1));
            assertEquals(0, sampleAmount.compareTo(amountCaptor.getValue()));
            assertEquals("owner@example.com", emailCaptor.getValue());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenAnonymousUserTriesTransfer")
        void shouldReturnUnauthorizedWhenAnonymousUserTriesTransfer() throws Exception {
            TransferRequestDto requestDto = new TransferRequestDto();
            requestDto.setSendCardId(sampleCardId1);
            requestDto.setReceiveCardId(sampleCardId2);
            requestDto.setAmount(sampleAmount);
            mockMvc.perform(post(BASE_URL + "/transfer-between-cards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(transactionService, never()).transferBetweenCards(any(), any(), any(), any());
        }

        @Test
        @WithMockUser(username = "other.user@example.com")
        @DisplayName("shouldReturnForbiddenWhenUserDoesNotOwnCards")
        void shouldReturnForbiddenWhenUserDoesNotOwnCards() throws Exception {
            TransferRequestDto requestDto = new TransferRequestDto();
            requestDto.setSendCardId(sampleCardId1);
            requestDto.setReceiveCardId(sampleCardId2);
            requestDto.setAmount(sampleAmount);
            doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: You are not allowed to perform transfers between these cards"))
                    .when(transactionService).transferBetweenCards(any(), any(), any(), eq("other.user@example.com"));
            mockMvc.perform(post(BASE_URL + "/transfer-between-cards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(transactionService, times(1)).transferBetweenCards(any(), any(), any(), eq("other.user@example.com"));
        }

        @Test
        @WithMockUser(username = "user@example.com")
        @DisplayName("shouldReturnNotFoundWhenCardNotFound")
        void shouldReturnNotFoundWhenCardNotFound() throws Exception {
            TransferRequestDto requestDto = new TransferRequestDto();
            requestDto.setSendCardId(UUID.randomUUID());
            requestDto.setReceiveCardId(sampleCardId2);
            requestDto.setAmount(sampleAmount);
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found: Card not found"))
                    .when(transactionService).transferBetweenCards(any(), any(), any(), eq("user@example.com"));
            mockMvc.perform(post(BASE_URL + "/transfer-between-cards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isNotFound());
            verify(transactionService, times(1)).transferBetweenCards(any(), any(), any(), eq("user@example.com"));
        }

        @Test
        @WithMockUser(username = "user@example.com")
        @DisplayName("shouldReturnUnprocessableEntityWhenInsufficientFunds")
        void shouldReturnUnprocessableEntityWhenInsufficientFunds() throws Exception {
            TransferRequestDto requestDto = new TransferRequestDto();
            requestDto.setSendCardId(sampleCardId1);
            requestDto.setReceiveCardId(sampleCardId2);
            requestDto.setAmount(new BigDecimal("1000000"));
            doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity: Insufficient funds"))
                    .when(transactionService).transferBetweenCards(any(), any(), any(), eq("user@example.com"));
            mockMvc.perform(post(BASE_URL + "/transfer-between-cards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isUnprocessableEntity());
            verify(transactionService, times(1)).transferBetweenCards(any(), any(), any(), eq("user@example.com"));
        }

        @Test
        @WithMockUser(username = "user@example.com")
        @DisplayName("shouldReturnLockedWhenCardIsBlocked")
        void shouldReturnLockedWhenCardIsBlocked() throws Exception {
            TransferRequestDto requestDto = new TransferRequestDto();
            requestDto.setSendCardId(sampleCardId1);
            requestDto.setReceiveCardId(sampleCardId2);
            requestDto.setAmount(sampleAmount);
            doThrow(new ResponseStatusException(HttpStatus.LOCKED, "Locked: The card is blocked"))
                    .when(transactionService).transferBetweenCards(any(), any(), any(), eq("user@example.com"));
            mockMvc.perform(post(BASE_URL + "/transfer-between-cards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isLocked());
            verify(transactionService, times(1)).transferBetweenCards(any(), any(), any(), eq("user@example.com"));
        }

        @Test
        @WithMockUser(username = "user@example.com")
        @DisplayName("shouldReturnBadRequestWhenAmountIsNegative")
        void shouldReturnBadRequestWhenAmountIsNegative() throws Exception {
            TransferRequestDto requestDto = new TransferRequestDto();
            requestDto.setSendCardId(sampleCardId1);
            requestDto.setReceiveCardId(sampleCardId2);
            requestDto.setAmount(new BigDecimal("-10.00"));
            mockMvc.perform(post(BASE_URL + "/transfer-between-cards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
            verify(transactionService, never()).transferBetweenCards(any(), any(), any(), any());
        }

        @Test
        @WithMockUser(username = "user@example.com")
        @DisplayName("shouldReturnBadRequestWhenCardIdIsNull")
        void shouldReturnBadRequestWhenCardIdIsNull() throws Exception {
            TransferRequestDto requestDto = new TransferRequestDto();
            requestDto.setSendCardId(null);
            requestDto.setReceiveCardId(sampleCardId2);
            requestDto.setAmount(sampleAmount);
            mockMvc.perform(post(BASE_URL + "/transfer-between-cards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            verify(transactionService, never()).transferBetweenCards(any(), any(), any(), any());
        }
    }


    @Nested
    @DisplayName("PUT /api/transaction/debit-from-card Tests")
    class DebitFromCardTests {

        @Test
        @WithMockUser(username = "owner@example.com")
        @DisplayName("shouldDebitSuccessfullyWhenUserAuthenticatedAndOwnsCard")
        void shouldDebitSuccessfullyWhenUserAuthenticatedAndOwnsCard() throws Exception {
            DebitRequestDto requestDto = new DebitRequestDto();
            requestDto.setCardId(sampleCardId1);
            requestDto.setAmount(sampleAmount);
            doNothing().when(transactionService).debitFromCard(eq(sampleCardId1), eq(sampleAmount), eq("owner@example.com"));
            mockMvc.perform(put(BASE_URL + "/debit-from-card")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string(isEmptyOrNullString()));
            verify(transactionService, times(1)).debitFromCard(uuidCaptor.capture(), amountCaptor.capture(), emailCaptor.capture());
            assertEquals(sampleCardId1, uuidCaptor.getValue());
            assertEquals(0, sampleAmount.compareTo(amountCaptor.getValue()));
            assertEquals("owner@example.com", emailCaptor.getValue());
        }

        @Test
        @WithMockUser(username = "user@example.com")
        @DisplayName("shouldReturnUnprocessableEntityWhenLimitExceeded")
        void shouldReturnUnprocessableEntityWhenLimitExceeded() throws Exception {
            DebitRequestDto requestDto = new DebitRequestDto();
            requestDto.setCardId(sampleCardId1);
            requestDto.setAmount(new BigDecimal("5000"));
            doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity: Limit exceeded"))
                    .when(transactionService).debitFromCard(any(), any(), eq("user@example.com"));
            mockMvc.perform(put(BASE_URL + "/debit-from-card")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isUnprocessableEntity());
            verify(transactionService, times(1)).debitFromCard(any(), any(), eq("user@example.com"));
        }

    }


    @Nested
    @DisplayName("PUT /api/transaction/top-up-card Tests")
    class TopUpCardTests {

        @Test
        @WithMockUser(username = "owner@example.com")
        @DisplayName("shouldTopUpSuccessfullyWhenUserAuthenticatedAndOwnsCard")
        void shouldTopUpSuccessfullyWhenUserAuthenticatedAndOwnsCard() throws Exception {
            TopUpRequestDto requestDto = new TopUpRequestDto();
            requestDto.setCardId(sampleCardId1);
            requestDto.setAmount(sampleAmount);
            doNothing().when(transactionService).topUpCard(eq(sampleCardId1), eq(sampleAmount), eq("owner@example.com"));
            mockMvc.perform(put(BASE_URL + "/top-up-card")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string(isEmptyOrNullString()));
            verify(transactionService, times(1)).topUpCard(uuidCaptor.capture(), amountCaptor.capture(), emailCaptor.capture());
            assertEquals(sampleCardId1, uuidCaptor.getValue());
            assertEquals(0, sampleAmount.compareTo(amountCaptor.getValue()));
            assertEquals("owner@example.com", emailCaptor.getValue());
        }

        @Test
        @WithMockUser(username = "user@example.com")
        @DisplayName("shouldReturnUnprocessableEntityWhenCardExpired")
        void shouldReturnUnprocessableEntityWhenCardExpired() throws Exception {
            TopUpRequestDto requestDto = new TopUpRequestDto();
            requestDto.setCardId(sampleCardId1);
            requestDto.setAmount(sampleAmount);
            doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity: Card has expired"))
                    .when(transactionService).topUpCard(any(), any(), eq("user@example.com"));
            mockMvc.perform(put(BASE_URL + "/top-up-card")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andDo(print())
                    .andExpect(status().isUnprocessableEntity());
            verify(transactionService, times(1)).topUpCard(any(), any(), eq("user@example.com"));
        }

    }


    @Nested
    @DisplayName("GET /api/transaction/my-transactions Tests")
    class GetMyTransactionsTests {

        @Test
        @WithMockUser(username = "filter.user@example.com")
        @DisplayName("shouldReturnCurrentUserTransactionsWithFiltersAndPagination")
        void shouldReturnCurrentUserTransactionsWithFiltersAndPagination() throws Exception {
            TransactionType filterType = TransactionType.CREDIT;
            BigDecimal filterAmountFrom = new BigDecimal("100.00");
            LocalDateTime filterDateFrom = LocalDateTime.of(2024, 1, 15, 10, 0, 0);
            UUID filterCardId = sampleCardId1;
            int page = 1;
            int size = 5;
            String sortField = "amount";
            String sortDir = "asc";
            List<TransactionDto> filteredTransactions = Collections.singletonList(
                    createSampleTransactionDto(UUID.randomUUID(), filterType, null, "**** **** **** 1234", filterAmountFrom.add(BigDecimal.TEN))
            );
            Pageable expectedPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, sortField));
            when(transactionService.getTransactions(eq("filter.user@example.com"), filterCaptor.capture(), pageableCaptor.capture()))
                    .thenReturn(filteredTransactions);
            mockMvc.perform(get(BASE_URL + "/my-transactions")
                            .param("type", filterType.name())
                            .param("amountFrom", filterAmountFrom.toPlainString())
                            .param("createdAtFrom", filterDateFrom.format(ISO_FORMATTER))
                            .param("cardId", filterCardId.toString())
                            .param("page", String.valueOf(page))
                            .param("size", String.valueOf(size))
                            .param("sort", sortField + "," + sortDir))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].type", is(filterType.name())))
                    .andExpect(jsonPath("$[0].amount", greaterThanOrEqualTo(filterAmountFrom.doubleValue())));
            assertEquals(expectedPageable, pageableCaptor.getValue());
            TransactionFilter capturedFilter = filterCaptor.getValue();
            assertNotNull(capturedFilter);
            assertEquals(filterType, capturedFilter.getType());
            assertEquals(0, filterAmountFrom.compareTo(capturedFilter.getAmountFrom()));
            assertNull(capturedFilter.getAmountTo());
            assertEquals(filterDateFrom, capturedFilter.getCreatedAtFrom());
            assertNull(capturedFilter.getCreatedAtTo());
            assertEquals(filterCardId, capturedFilter.getCardId());
            verify(transactionService, times(1)).getTransactions(eq("filter.user@example.com"), any(TransactionFilter.class), any(Pageable.class));
        }

        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenAnonymousUserTriesToGetMyTransactions")
        void shouldReturnUnauthorizedWhenAnonymousUserTriesToGetMyTransactions() throws Exception {
            mockMvc.perform(get(BASE_URL + "/my-transactions"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(transactionService, never()).getTransactions(any(), any(), any());
        }
    }


    @Nested
    @DisplayName("GET /api/transaction/transactions-by-user Tests")
    class GetTransactionsByUserTests {


        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnFilteredTransactionsWhenAdminWithAllFilters")
        void shouldReturnFilteredTransactionsWhenAdminWithAllFilters() throws Exception {
            String targetUserEmail = "target@example.com";
            TransactionType filterType = TransactionType.DEBIT;
            Pageable expectedPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            List<TransactionDto> transactions = Arrays.asList(sampleTransactionDto);
            when(transactionService.getTransactions(eq(targetUserEmail), filterCaptor.capture(), pageableCaptor.capture()))
                    .thenReturn(transactions);
            mockMvc.perform(get(BASE_URL + "/transactions-by-user")
                                    .param("userEmail", targetUserEmail)
                                    .param("type", filterType.name())
                    )
                    .andDo(print())
                    .andExpect(status().isOk());
            assertEquals(expectedPageable, pageableCaptor.getValue());
            assertEquals(filterType, filterCaptor.getValue().getType());
            verify(transactionService, times(1)).getTransactions(eq(targetUserEmail), any(TransactionFilter.class), any(Pageable.class));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("shouldReturnForbiddenWhenUserTriesToGetTransactionsByEmail")
        void shouldReturnForbiddenWhenUserTriesToGetTransactionsByEmail() throws Exception {
            mockMvc.perform(get(BASE_URL + "/transactions-by-user"))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(transactionService, never()).getTransactions(any(), any(), any());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenAnonymousUserTriesToGetTransactionsByEmail")
        void shouldReturnUnauthorizedWhenAnonymousUserTriesToGetTransactionsByEmail() throws Exception {
            mockMvc.perform(get(BASE_URL + "/transactions-by-user"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(transactionService, never()).getTransactions(any(), any(), any());
        }
    }
}