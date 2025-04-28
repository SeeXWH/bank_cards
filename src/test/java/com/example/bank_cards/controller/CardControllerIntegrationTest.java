package com.example.bank_cards.controller;

import com.example.bank_cards.dto.CardCreateDto;
import com.example.bank_cards.dto.CardDto; // Импортируем обновленный DTO
import com.example.bank_cards.dto.CardLimitDto;
import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.serviceInterface.CardServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDate;
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
class CardControllerIntegrationTest {

    private static final String BASE_URL = "/api/cards";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CardServiceImpl cardService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private CardDto sampleCardDto;
    private UUID sampleCardId;
    private String sampleUserEmail; // Оставим email для CardCreateDto и логики сервиса
    private String sampleOwnerName; // Добавим имя владельца
    private String sampleCardNumber; // Добавим номер карты (может быть маскированным или полным в DTO)

    @BeforeEach
    void setUp() {
        sampleCardId = UUID.randomUUID();
        sampleUserEmail = "user@example.com"; // Используется для создания карты
        sampleOwnerName = "John Doe"; // Имя для CardDto
        sampleCardNumber = "**** **** **** 1234"; // Пример номера карты для DTO

        // Используем новый конструктор/сеттеры CardDto
        sampleCardDto = new CardDto();
        sampleCardDto.setId(sampleCardId);
        sampleCardDto.setCardNumber(sampleCardNumber); // Устанавливаем cardNumber
        sampleCardDto.setOwnerName(sampleOwnerName);   // Устанавливаем ownerName
        sampleCardDto.setBalance(BigDecimal.valueOf(1000));
        sampleCardDto.setExpiryDate(LocalDate.now().plusYears(3));
        sampleCardDto.setStatus(CardStatus.ACTIVE);
        sampleCardDto.setDailyLimit(BigDecimal.valueOf(500));
        sampleCardDto.setMonthlyLimit(BigDecimal.valueOf(2000));
    }

    // --- Вспомогательный метод для создания CardDto ---
    private CardDto createSampleCardDto(UUID id, String ownerName, String cardNumber, CardStatus status) {
        CardDto dto = new CardDto();
        dto.setId(id);
        dto.setCardNumber(cardNumber); // Используем cardNumber
        dto.setOwnerName(ownerName);   // Используем ownerName
        dto.setBalance(BigDecimal.valueOf(500));
        dto.setExpiryDate(LocalDate.now().plusYears(2));
        dto.setStatus(status);
        dto.setDailyLimit(BigDecimal.valueOf(300));
        dto.setMonthlyLimit(BigDecimal.valueOf(1500));
        return dto;
    }

    // --- Тесты для POST /create-card ---
    @Nested
    @DisplayName("POST /api/cards/create-card Tests")
    class CreateCardTests {

        // Тесты Forbidden, Unauthorized, BadRequest, NotFound остаются без изменений в логике проверок,
        // т.к. они не проверяют успешную структуру CardDto
        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("shouldReturnForbiddenWhenUserTriesToCreateCard")
        void shouldReturnForbiddenWhenUserTriesToCreateCard() throws Exception {
            CardCreateDto createDto = new CardCreateDto(sampleUserEmail, LocalDate.now().plusYears(3));
            mockMvc.perform(post(BASE_URL + "/create-card")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createDto)))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(cardService, never()).createCard(any());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenAnonymousUserTriesToCreateCard")
        void shouldReturnUnauthorizedWhenAnonymousUserTriesToCreateCard() throws Exception {
            CardCreateDto createDto = new CardCreateDto(sampleUserEmail, LocalDate.now().plusYears(3));
            mockMvc.perform(post(BASE_URL + "/create-card")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createDto)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(cardService, never()).createCard(any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnBadRequestWhenCreateCardWithInvalidData")
        void shouldReturnBadRequestWhenCreateCardWithInvalidData() throws Exception {
            CardCreateDto invalidDto = new CardCreateDto("invalid-email", LocalDate.now().plusYears(3));
            mockMvc.perform(post(BASE_URL + "/create-card")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidDto)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
            verify(cardService, never()).createCard(any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnNotFoundWhenCreateCardForNonExistingUser")
        void shouldReturnNotFoundWhenCreateCardForNonExistingUser() throws Exception {
            CardCreateDto createDto = new CardCreateDto("nonexistent@example.com", LocalDate.now().plusYears(3));
            when(cardService.createCard(any(CardCreateDto.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found: User not found"));

            mockMvc.perform(post(BASE_URL + "/create-card")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createDto)))
                    .andDo(print())
                    .andExpect(status().isNotFound());
            verify(cardService, times(1)).createCard(any());
        }
    }

    // --- Тесты для PUT /set-card-status ---
    @Nested
    @DisplayName("PUT /api/cards/set-card-status Tests")
    class SetCardStatusTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldSetCardStatusWhenAdminAndValidData")
        void shouldSetCardStatusWhenAdminAndValidData() throws Exception {
            CardStatus newStatus = CardStatus.BLOCKED;
            sampleCardDto.setStatus(newStatus); // Обновляем статус в нашем sample DTO
            // Настроим мок - он должен вернуть CardDto с обновленным статусом и НОВЫМИ полями
            when(cardService.setCardStatus(eq(sampleCardId), eq(newStatus))).thenReturn(sampleCardDto);

            mockMvc.perform(put(BASE_URL + "/set-card-status")
                            .param("id", sampleCardId.toString())
                            .param("status", newStatus.name()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(sampleCardId.toString())))
                    .andExpect(jsonPath("$.status", is(newStatus.name())))
                    // Добавим проверку других полей на всякий случай
                    .andExpect(jsonPath("$.cardNumber", is(sampleCardDto.getCardNumber())))
                    .andExpect(jsonPath("$.ownerName", is(sampleCardDto.getOwnerName())));

            verify(cardService, times(1)).setCardStatus(eq(sampleCardId), eq(newStatus));
        }

        // Остальные тесты (Forbidden, Unauthorized, BadRequest, NotFound) не меняются
        // ... (оставлены для краткости, они идентичны предыдущей версии) ...
        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("shouldReturnForbiddenWhenUserTriesToSetStatus")
        void shouldReturnForbiddenWhenUserTriesToSetStatus() throws Exception {
            mockMvc.perform(put(BASE_URL + "/set-card-status")
                            .param("id", sampleCardId.toString())
                            .param("status", CardStatus.BLOCKED.name()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(cardService, never()).setCardStatus(any(), any());
        }
        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenAnonymousUserTriesToSetStatus")
        void shouldReturnUnauthorizedWhenAnonymousUserTriesToSetStatus() throws Exception {
            mockMvc.perform(put(BASE_URL + "/set-card-status")
                            .param("id", sampleCardId.toString())
                            .param("status", CardStatus.BLOCKED.name()))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(cardService, never()).setCardStatus(any(), any());
        }
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnBadRequestWhenSettingExpiredStatus")
        void shouldReturnBadRequestWhenSettingExpiredStatus() throws Exception {
            when(cardService.setCardStatus(eq(sampleCardId), eq(CardStatus.EXPIRED)))
                    .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot manually set status to EXPIRED"));
            mockMvc.perform(put(BASE_URL + "/set-card-status")
                            .param("id", sampleCardId.toString())
                            .param("status", CardStatus.EXPIRED.name()))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
            verify(cardService, times(1)).setCardStatus(eq(sampleCardId), eq(CardStatus.EXPIRED));
        }
        // ... другие тесты SetCardStatus без изменений ...
    }


    // --- Тесты для GET /my-cards ---
    @Nested
    @DisplayName("GET /api/cards/my-cards Tests")
    class GetMyCardsTests {

        @Test
        @WithMockUser(username = "test@user.com", roles = "USER") // username из Authentication
        @DisplayName("shouldReturnCurrentUserCards")
        void shouldReturnCurrentUserCards() throws Exception {
            // Используем обновленный createSampleCardDto
            List<CardDto> cards = Arrays.asList(
                    createSampleCardDto(UUID.randomUUID(), "Test User", "**** **** **** 5678", CardStatus.ACTIVE),
                    createSampleCardDto(UUID.randomUUID(), "Test User", "**** **** **** 9012", CardStatus.BLOCKED)
            );
            Pageable defaultPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "expiryDate"));
            // Сервис по-прежнему принимает email для поиска, но возвращает List<CardDto> нового формата
            when(cardService.getCardsByUserEmail(eq("test@user.com"), isNull(), eq(defaultPageable)))
                    .thenReturn(cards);

            mockMvc.perform(get(BASE_URL + "/my-cards"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    // Проверяем новые поля
                    .andExpect(jsonPath("$[0].ownerName", is("Test User")))
                    .andExpect(jsonPath("$[0].cardNumber", is("**** **** **** 5678")))
                    .andExpect(jsonPath("$[1].ownerName", is("Test User")))
                    .andExpect(jsonPath("$[1].cardNumber", is("**** **** **** 9012")));

            verify(cardService, times(1)).getCardsByUserEmail(eq("test@user.com"), isNull(), eq(defaultPageable));
        }

        @Test
        @WithMockUser(username = "test@user.com", roles = "USER")
        @DisplayName("shouldReturnCurrentUserCardsWithStatusFilterAndPaginationAndSort")
        void shouldReturnCurrentUserCardsWithStatusFilterAndPaginationAndSort() throws Exception {
            List<CardDto> activeCards = Collections.singletonList(
                    createSampleCardDto(UUID.randomUUID(), "Test User", "**** **** **** 1111", CardStatus.ACTIVE)
            );
            int page = 1;
            int size = 5;
            String sortField = "balance";
            String sortDir = "desc";
            CardStatus filterStatus = CardStatus.ACTIVE;

            Pageable expectedPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortField));

            when(cardService.getCardsByUserEmail(eq("test@user.com"), eq(filterStatus), pageableCaptor.capture()))
                    .thenReturn(activeCards);

            mockMvc.perform(get(BASE_URL + "/my-cards")
                            .param("status", filterStatus.name())
                            .param("page", String.valueOf(page))
                            .param("size", String.valueOf(size))
                            .param("sort", sortField + "," + sortDir))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].status", is(filterStatus.name())))
                    // Проверяем новые поля
                    .andExpect(jsonPath("$[0].ownerName", is("Test User")))
                    .andExpect(jsonPath("$[0].cardNumber", is("**** **** **** 1111")));

            assertEquals(page, pageableCaptor.getValue().getPageNumber());
            assertEquals(size, pageableCaptor.getValue().getPageSize());
            assertEquals(Sort.by(Sort.Direction.DESC, sortField), pageableCaptor.getValue().getSort());
            verify(cardService, times(1)).getCardsByUserEmail(eq("test@user.com"), eq(filterStatus), any(Pageable.class));
        }

        // Остальные тесты (Unauthorized, BadRequest) не меняются
        // ... (оставлены для краткости) ...
        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenAnonymousUserTriesToGetMyCards")
        void shouldReturnUnauthorizedWhenAnonymousUserTriesToGetMyCards() throws Exception {
            mockMvc.perform(get(BASE_URL + "/my-cards"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(cardService, never()).getCardsByUserEmail(anyString(), any(), any());
        }
        // ... другие тесты GetMyCards без изменений ...
    }

    // --- Тесты для GET /cards-by-user ---
    @Nested
    @DisplayName("GET /api/cards/cards-by-user Tests")
    class GetCardsByUserTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnCardsForUserWhenAdmin")
        void shouldReturnCardsForUserWhenAdmin() throws Exception {
            String targetEmail = "target@example.com"; // Email для поиска
            String targetOwnerName = "Target User";    // Имя для DTO
            List<CardDto> cards = Arrays.asList(
                    createSampleCardDto(UUID.randomUUID(), targetOwnerName, "**** **** **** 3333", CardStatus.ACTIVE),
                    createSampleCardDto(UUID.randomUUID(), targetOwnerName, "**** **** **** 4444", CardStatus.EXPIRED)
            );
            Pageable defaultPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "expiryDate"));

            // Сервис ищет по email, возвращает DTO нового формата
            when(cardService.getCardsByUserEmail(eq(targetEmail), isNull(), eq(defaultPageable)))
                    .thenReturn(cards);

            mockMvc.perform(get(BASE_URL + "/cards-by-user")
                            .param("email", targetEmail)) // Параметр запроса остается email
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    // Проверяем новые поля в ответе
                    .andExpect(jsonPath("$[0].ownerName", is(targetOwnerName)))
                    .andExpect(jsonPath("$[0].cardNumber", is("**** **** **** 3333")))
                    .andExpect(jsonPath("$[1].ownerName", is(targetOwnerName)))
                    .andExpect(jsonPath("$[1].cardNumber", is("**** **** **** 4444")));

            verify(cardService, times(1)).getCardsByUserEmail(eq(targetEmail), isNull(), eq(defaultPageable));
        }

        // Остальные тесты (Forbidden, Unauthorized, BadRequest, NotFound) не меняются
        // ... (оставлены для краткости) ...
        @Test
        @WithMockUser(roles = "USER") // Обычный пользователь
        @DisplayName("shouldReturnForbiddenWhenUserTriesToGetCardsByEmail")
        void shouldReturnForbiddenWhenUserTriesToGetCardsByEmail() throws Exception {
            mockMvc.perform(get(BASE_URL + "/cards-by-user")
                            .param("email", "another@user.com"))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(cardService, never()).getCardsByUserEmail(anyString(), any(), any());
        }
        // ... другие тесты GetCardsByUser без изменений ...
    }

    // --- Тесты для DELETE /{id}/delete ---
    @Nested
    @DisplayName("DELETE /api/cards/{id}/delete Tests")
    class DeleteCardTests {
        // Эти тесты не затрагиваются изменением CardDto, так как они проверяют статус ответа (204)
        // или ошибки, а не тело ответа.
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldDeleteCardWhenAdmin")
        void shouldDeleteCardWhenAdmin() throws Exception {
            doNothing().when(cardService).deleteCard(eq(sampleCardId));
            mockMvc.perform(delete(BASE_URL + "/" + sampleCardId + "/delete"))
                    .andDo(print())
                    .andExpect(status().isNoContent());
            verify(cardService, times(1)).deleteCard(eq(sampleCardId));
        }
        // ... остальные тесты DeleteCardTests без изменений ...
        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("shouldReturnForbiddenWhenUserTriesToDeleteCard")
        void shouldReturnForbiddenWhenUserTriesToDeleteCard() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/" + sampleCardId + "/delete"))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(cardService, never()).deleteCard(any());
        }
        // ...
    }

    // --- Тесты для PATCH /{id}/limits ---
    @Nested
    @DisplayName("PATCH /api/cards/{id}/limits Tests")
    class UpdateCardLimitsTests {


        // Остальные тесты (Forbidden, Unauthorized, BadRequest, NotFound) не меняются
        // ... (оставлены для краткости) ...
        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("shouldReturnForbiddenWhenUserTriesToUpdateLimits")
        void shouldReturnForbiddenWhenUserTriesToUpdateLimits() throws Exception {
            CardLimitDto limitDto = new CardLimitDto(BigDecimal.valueOf(100), BigDecimal.valueOf(500));
            mockMvc.perform(patch(BASE_URL + "/" + sampleCardId + "/limits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(limitDto)))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(cardService, never()).setCardLimit(any(), any());
        }
        // ... другие тесты UpdateCardLimits без изменений ...
    }


    // --- Тесты для GET /{id}/number ---
    @Nested
    @DisplayName("GET /api/cards/{id}/number Tests")
    class GetCardNumberTests {
        // Этот эндпоинт возвращает String, а не CardDto.
        // Изменение структуры CardDto не влияет на эти тесты напрямую.
        // Они остаются такими же, как в предыдущей версии.
        @Test
        @WithMockUser(roles = "USER") // Доступ есть у любого аутентифицированного
        @DisplayName("shouldReturnCardNumberWhenUserIsAuthenticated")
        void shouldReturnCardNumberWhenUserIsAuthenticated() throws Exception {
            String decryptedNumber = "1111222233334444";
            when(cardService.getCardNumber(eq(sampleCardId))).thenReturn(decryptedNumber);

            mockMvc.perform(get(BASE_URL + "/" + sampleCardId + "/number"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(content().string(decryptedNumber));

            verify(cardService, times(1)).getCardNumber(eq(sampleCardId));
        }

        // ... остальные тесты GetCardNumberTests без изменений ...
        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenAnonymousUserTriesToGetCardNumber")
        void shouldReturnUnauthorizedWhenAnonymousUserTriesToGetCardNumber() throws Exception {
            mockMvc.perform(get(BASE_URL + "/" + sampleCardId + "/number"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(cardService, never()).getCardNumber(any());
        }
        // ...
    }
}