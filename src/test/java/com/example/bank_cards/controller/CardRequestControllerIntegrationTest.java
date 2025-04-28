package com.example.bank_cards.controller;

import com.example.bank_cards.dto.BlockCardRequestDto;
import com.example.bank_cards.enums.RequestStatus;
import com.example.bank_cards.enums.RequestType;
import com.example.bank_cards.enums.Role; 
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.CardRequest;
import com.example.bank_cards.serviceInterface.CardRequestServiceImpl;
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
class CardRequestControllerIntegrationTest {

    private static final String BASE_URL = "/api/card-requests";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    private static ObjectMapper objectMapper;

    @MockBean
    private CardRequestServiceImpl cardRequestService;

    @Captor private ArgumentCaptor<Pageable> pageableCaptor;
    @Captor private ArgumentCaptor<BlockCardRequestDto> blockDtoCaptor;
    @Captor private ArgumentCaptor<String> emailCaptor;
    @Captor private ArgumentCaptor<UUID> uuidCaptor;
    @Captor private ArgumentCaptor<RequestStatus> statusCaptor;
    @Captor private ArgumentCaptor<RequestType> typeCaptor;
    @Captor private ArgumentCaptor<LocalDateTime> dateCaptor;

    private String sampleUserEmail;
    private String sampleAdminEmail;
    private String sampleCardNumber;
    private String sampleUserName;
    private String sampleUserPassword; 
    private UUID sampleRequestId;
    private UUID sampleOwnerId;
    private UUID sampleCardId;
    private AppUser sampleOwner;
    private CardRequest sampleCardRequest;

    @BeforeAll
    static void beforeAll() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @BeforeEach
    void setUp() {
        sampleUserEmail = "user@example.com";
        sampleAdminEmail = "admin@example.com";
        sampleCardNumber = "1111222233334444";
        sampleUserName = "Test User";
        sampleUserPassword = "password123"; 
        sampleRequestId = UUID.randomUUID();
        sampleOwnerId = UUID.randomUUID();
        sampleCardId = UUID.randomUUID();
        sampleOwner = new AppUser();
        sampleOwner.setId(sampleOwnerId);
        sampleOwner.setEmail(sampleUserEmail);
        sampleOwner.setName(sampleUserName);
        sampleOwner.setPassword(sampleUserPassword); 
        sampleOwner.setRole(Role.USER);
        sampleOwner.setLocked(false);
        sampleCardRequest = new CardRequest();
        sampleCardRequest.setId(sampleRequestId);
        sampleCardRequest.setOwner(sampleOwner);
        sampleCardRequest.setType(RequestType.CREATE_CARD);
        sampleCardRequest.setStatus(RequestStatus.PENDING);
        sampleCardRequest.setCreatedAt(LocalDateTime.now().minusDays(1).withNano(0));
        sampleCardRequest.setCardId(null);
    }

    
    private AppUser createSampleUser(UUID id, String email, String name, Role role, boolean locked) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setPassword("somehashedpassword"); 
        user.setRole(role);
        user.setLocked(locked);
        return user;
    }

    
    private CardRequest createSampleRequest(UUID reqId, AppUser owner, RequestType type, RequestStatus status, UUID cardId) {
        CardRequest req = new CardRequest();
        req.setId(reqId);
        req.setOwner(owner);
        req.setType(type);
        req.setStatus(status);
        req.setCreatedAt(LocalDateTime.now().withNano(0));
        req.setCardId(cardId);
        return req;
    }

    
    @Nested
    @DisplayName("POST /api/card-requests/create-card Tests")
    class CreateCardRequestTests {
        
        @Test
        @WithMockUser(username = "test.user@example.com", roles = "USER")
        @DisplayName("shouldCreateCardRequestWhenUserIsAuthenticated")
        void shouldCreateCardRequestWhenUserIsAuthenticated() throws Exception {
            doNothing().when(cardRequestService).createRequestToCreateCard(eq("test.user@example.com"));
            mockMvc.perform(post(BASE_URL + "/create-card"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string(isEmptyOrNullString()));
            verify(cardRequestService, times(1)).createRequestToCreateCard(eq("test.user@example.com"));
        }
        
        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenAnonymousUserTriesToCreateRequest")
        void shouldReturnUnauthorizedWhenAnonymousUserTriesToCreateRequest() throws Exception {
            mockMvc.perform(post(BASE_URL + "/create-card"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(cardRequestService, never()).createRequestToCreateCard(anyString());
        }
        
    }

    
    @Nested
    @DisplayName("POST /api/card-requests/block-card Tests")
    class BlockCardRequestTests {
        
        @Test
        @WithMockUser(username = "owner@example.com", roles = "USER")
        @DisplayName("shouldCreateBlockRequestWhenUserIsAuthenticatedAndOwnsCard")
        void shouldCreateBlockRequestWhenUserIsAuthenticatedAndOwnsCard() throws Exception {
            BlockCardRequestDto blockDto = new BlockCardRequestDto(sampleCardNumber);
            doNothing().when(cardRequestService).createRequestToBlockCard(eq("owner@example.com"), any(BlockCardRequestDto.class));
            mockMvc.perform(post(BASE_URL + "/block-card")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(blockDto))) 
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string(isEmptyOrNullString()));
            verify(cardRequestService, times(1)).createRequestToBlockCard(emailCaptor.capture(), blockDtoCaptor.capture());
            assertEquals("owner@example.com", emailCaptor.getValue());
            assertEquals(sampleCardNumber, blockDtoCaptor.getValue().getCardNumber());
        }
        
        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenAnonymousUserTriesToBlockCard")
        void shouldReturnUnauthorizedWhenAnonymousUserTriesToBlockCard() throws Exception {
            BlockCardRequestDto blockDto = new BlockCardRequestDto(sampleCardNumber);
            mockMvc.perform(post(BASE_URL + "/block-card")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(blockDto)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(cardRequestService, never()).createRequestToBlockCard(anyString(), any());
        }
        
    }


    
    @Nested
    @DisplayName("PUT /api/card-requests/set-status Tests")
    class SetRequestStatusTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldUpdateRequestStatusWhenAdmin")
        void shouldUpdateRequestStatusWhenAdmin() throws Exception {
            RequestStatus newStatus = RequestStatus.APPROVED;
            UUID cardIdForRequest = UUID.randomUUID();
            sampleCardRequest.setStatus(newStatus);
            sampleCardRequest.setCardId(cardIdForRequest);
            sampleCardRequest.setType(RequestType.BLOCK_CARD);
            when(cardRequestService.setRequestStatus(eq(sampleRequestId), eq(newStatus))).thenReturn(sampleCardRequest);
            mockMvc.perform(put(BASE_URL + "/set-status")
                            .param("requestId", sampleRequestId.toString())
                            .param("requestStatus", newStatus.name()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(sampleRequestId.toString())))
                    .andExpect(jsonPath("$.status", is(newStatus.name())))
                    .andExpect(jsonPath("$.owner.id", is(sampleOwnerId.toString())))
                    .andExpect(jsonPath("$.owner.email", is(sampleUserEmail)))
                    .andExpect(jsonPath("$.owner.name", is(sampleUserName)))
                    .andExpect(jsonPath("$.owner.role", is(Role.USER.name()))) 
                    .andExpect(jsonPath("$.owner.locked", is(false)))
                    .andExpect(jsonPath("$.owner.password", is(sampleUserPassword)))
                    .andExpect(jsonPath("$.type", is(sampleCardRequest.getType().name())))
                    .andExpect(jsonPath("$.cardId", is(cardIdForRequest.toString())))
                    .andExpect(jsonPath("$.createdAt", is(sampleCardRequest.getCreatedAt().format(ISO_FORMATTER))));
            verify(cardRequestService, times(1)).setRequestStatus(uuidCaptor.capture(), statusCaptor.capture());
            assertEquals(sampleRequestId, uuidCaptor.getValue());
            assertEquals(newStatus, statusCaptor.getValue());
        }
        
        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("shouldReturnForbiddenWhenUserTriesToSetStatus")
        void shouldReturnForbiddenWhenUserTriesToSetStatus() throws Exception {
            mockMvc.perform(put(BASE_URL + "/set-status")
                            .param("requestId", sampleRequestId.toString())
                            .param("requestStatus", RequestStatus.APPROVED.name()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(cardRequestService, never()).setRequestStatus(any(), any());
        }
        
    }

    
    @Nested
    @DisplayName("GET /api/card-requests/card-requests-by-user Tests")
    class GetCardRequestsByUserTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnRequestsWhenAdminWithDefaultParams")
        void shouldReturnRequestsWhenAdminWithDefaultParams() throws Exception {
            AppUser owner1 = createSampleUser(UUID.randomUUID(), sampleUserEmail, "User One", Role.USER, false);
            AppUser owner2 = createSampleUser(UUID.randomUUID(), sampleAdminEmail, "Admin User", Role.ADMIN, true);
            List<CardRequest> requests = Arrays.asList(
                    createSampleRequest(UUID.randomUUID(), owner1, RequestType.CREATE_CARD, RequestStatus.PENDING, null),
                    createSampleRequest(UUID.randomUUID(), owner2, RequestType.BLOCK_CARD, RequestStatus.APPROVED, UUID.randomUUID())
            );
            Pageable defaultPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(cardRequestService.getCardRequestsWithFilter(isNull(), isNull(), isNull(), isNull(), isNull(), eq(defaultPageable)))
                    .thenReturn(requests);
            mockMvc.perform(get(BASE_URL + "/card-requests-by-user"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id", is(requests.get(0).getId().toString())))
                    .andExpect(jsonPath("$[0].type", is(requests.get(0).getType().name())))
                    .andExpect(jsonPath("$[0].status", is(requests.get(0).getStatus().name())))
                    .andExpect(jsonPath("$[0].cardId", is(nullValue())))
                    .andExpect(jsonPath("$[0].createdAt", is(requests.get(0).getCreatedAt().format(ISO_FORMATTER))))
                    .andExpect(jsonPath("$[0].owner.id", is(owner1.getId().toString())))
                    .andExpect(jsonPath("$[0].owner.email", is(owner1.getEmail())))
                    .andExpect(jsonPath("$[0].owner.name", is(owner1.getName())))
                    .andExpect(jsonPath("$[0].owner.role", is(owner1.getRole().name())))
                    .andExpect(jsonPath("$[0].owner.locked", is(owner1.isLocked())))
                    .andExpect(jsonPath("$[0].owner.password", is(owner1.getPassword())))
                    .andExpect(jsonPath("$[1].id", is(requests.get(1).getId().toString())))
                    .andExpect(jsonPath("$[1].type", is(requests.get(1).getType().name())))
                    .andExpect(jsonPath("$[1].status", is(requests.get(1).getStatus().name())))
                    .andExpect(jsonPath("$[1].cardId", is(requests.get(1).getCardId().toString())))
                    .andExpect(jsonPath("$[1].createdAt", is(requests.get(1).getCreatedAt().format(ISO_FORMATTER))))
                    .andExpect(jsonPath("$[1].owner.id", is(owner2.getId().toString())))
                    .andExpect(jsonPath("$[1].owner.email", is(owner2.getEmail())))
                    .andExpect(jsonPath("$[1].owner.name", is(owner2.getName())))
                    .andExpect(jsonPath("$[1].owner.role", is(owner2.getRole().name())))
                    .andExpect(jsonPath("$[1].owner.locked", is(owner2.isLocked())))
                    .andExpect(jsonPath("$[1].owner.password", is(owner2.getPassword())));
            verify(cardRequestService, times(1)).getCardRequestsWithFilter(isNull(), isNull(), isNull(), isNull(), isNull(), eq(defaultPageable));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnRequestsWhenAdminWithAllFiltersAndPagination")
        void shouldReturnRequestsWhenAdminWithAllFiltersAndPagination() throws Exception {
            String filterEmail = "filter.user@example.com";
            AppUser filterOwner = createSampleUser(UUID.randomUUID(), filterEmail, "Filtered User", Role.USER, false);
            RequestType filterType = RequestType.BLOCK_CARD;
            RequestStatus filterStatus = RequestStatus.REJECTED;
            LocalDateTime filterFrom = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
            LocalDateTime filterTo = LocalDateTime.of(2024, 12, 31, 23, 59, 59);
            int page = 1;
            int size = 5;
            String sortField = "owner.name"; 
            String sortDir = "asc";
            Pageable expectedPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, sortField));
            List<CardRequest> filteredRequests = Collections.singletonList(
                    createSampleRequest(UUID.randomUUID(), filterOwner, filterType, filterStatus, UUID.randomUUID())
            );
            when(cardRequestService.getCardRequestsWithFilter(
                    eq(filterEmail), eq(filterType), eq(filterStatus), eq(filterFrom), eq(filterTo), pageableCaptor.capture()))
                    .thenReturn(filteredRequests);
            mockMvc.perform(get(BASE_URL + "/card-requests-by-user")
                            .param("email", filterEmail)
                            .param("type", filterType.name())
                            .param("status", filterStatus.name())
                            .param("from", filterFrom.format(ISO_FORMATTER))
                            .param("to", filterTo.format(ISO_FORMATTER))
                            .param("page", String.valueOf(page))
                            .param("size", String.valueOf(size))
                            .param("sort", sortField + "," + sortDir))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].type", is(filterType.name())))
                    .andExpect(jsonPath("$[0].status", is(filterStatus.name())))
                    .andExpect(jsonPath("$[0].cardId", is(filteredRequests.get(0).getCardId().toString())))
                    .andExpect(jsonPath("$[0].owner.id", is(filterOwner.getId().toString())))
                    .andExpect(jsonPath("$[0].owner.email", is(filterOwner.getEmail())))
                    .andExpect(jsonPath("$[0].owner.name", is(filterOwner.getName())));
            assertEquals(expectedPageable, pageableCaptor.getValue());
            verify(cardRequestService, times(1)).getCardRequestsWithFilter(
                    eq(filterEmail), eq(filterType), eq(filterStatus), eq(filterFrom), eq(filterTo), any(Pageable.class));
        }
        
        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("shouldReturnForbiddenWhenUserTriesToGetRequestsByEmail")
        void shouldReturnForbiddenWhenUserTriesToGetRequestsByEmail() throws Exception {
            mockMvc.perform(get(BASE_URL + "/card-requests-by-user"))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(cardRequestService, never()).getCardRequestsWithFilter(any(), any(), any(), any(), any(), any());
        }
        
    }

    
    @Nested
    @DisplayName("GET /api/card-requests/my-card-requests Tests")
    class GetMyCardRequestsTests {

        @Test
        @WithMockUser(username = "current.user@example.com", roles = "USER")
        @DisplayName("shouldReturnCurrentUserRequestsWithDefaultParams")
        void shouldReturnCurrentUserRequestsWithDefaultParams() throws Exception {
            AppUser currentUserOwner = createSampleUser(UUID.randomUUID(), "current.user@example.com", "Current User", Role.USER, false);
            List<CardRequest> requests = Arrays.asList(
                    createSampleRequest(UUID.randomUUID(), currentUserOwner, RequestType.CREATE_CARD, RequestStatus.PENDING, null),
                    createSampleRequest(UUID.randomUUID(), currentUserOwner, RequestType.BLOCK_CARD, RequestStatus.APPROVED, UUID.randomUUID())
            );
            Pageable defaultPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(cardRequestService.getCardRequestsWithFilter(eq("current.user@example.com"), isNull(), isNull(), isNull(), isNull(), eq(defaultPageable)))
                    .thenReturn(requests);
            mockMvc.perform(get(BASE_URL + "/my-card-requests"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].owner.email", is("current.user@example.com")))
                    .andExpect(jsonPath("$[0].owner.name", is("Current User")))
                    .andExpect(jsonPath("$[0].cardId", is(nullValue())))
                    .andExpect(jsonPath("$[1].owner.email", is("current.user@example.com")))
                    .andExpect(jsonPath("$[1].cardId", is(requests.get(1).getCardId().toString())));
            verify(cardRequestService, times(1)).getCardRequestsWithFilter(eq("current.user@example.com"), isNull(), isNull(), isNull(), isNull(), eq(defaultPageable));
        }

        @Test
        @WithMockUser(username = "current.user@example.com", roles = {"USER", "ADMIN"})
        @DisplayName("shouldReturnCurrentUserRequestsWithFilters")
        void shouldReturnCurrentUserRequestsWithFilters() throws Exception {
            AppUser currentUserOwner = createSampleUser(UUID.randomUUID(), "current.user@example.com", "Current User", Role.ADMIN, false); 
            RequestType filterType = RequestType.CREATE_CARD;
            RequestStatus filterStatus = RequestStatus.PENDING;
            Pageable defaultPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            List<CardRequest> filteredRequests = Collections.singletonList(
                    createSampleRequest(UUID.randomUUID(), currentUserOwner, filterType, filterStatus, null)
            );
            when(cardRequestService.getCardRequestsWithFilter(
                    eq("current.user@example.com"), eq(filterType), eq(filterStatus), isNull(), isNull(), pageableCaptor.capture()))
                    .thenReturn(filteredRequests);
            mockMvc.perform(get(BASE_URL + "/my-card-requests")
                            .param("type", filterType.name())
                            .param("status", filterStatus.name()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].type", is(filterType.name())))
                    .andExpect(jsonPath("$[0].status", is(filterStatus.name())))
                    .andExpect(jsonPath("$[0].cardId", is(nullValue())))
                    .andExpect(jsonPath("$[0].owner.id", is(currentUserOwner.getId().toString())))
                    .andExpect(jsonPath("$[0].owner.email", is(currentUserOwner.getEmail())))
                    .andExpect(jsonPath("$[0].owner.name", is(currentUserOwner.getName())))
                    .andExpect(jsonPath("$[0].owner.role", is(currentUserOwner.getRole().name())));
            assertEquals(defaultPageable, pageableCaptor.getValue());
            verify(cardRequestService, times(1)).getCardRequestsWithFilter(
                    eq("current.user@example.com"), eq(filterType), eq(filterStatus), isNull(), isNull(), any(Pageable.class));
        }
        
        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenAnonymousUserTriesToGetMyRequests")
        void shouldReturnUnauthorizedWhenAnonymousUserTriesToGetMyRequests() throws Exception {
            mockMvc.perform(get(BASE_URL + "/my-card-requests"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(cardRequestService, never()).getCardRequestsWithFilter(any(), any(), any(), any(), any(), any());
        }
        
    }
}