package com.vora.reservation.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vora.reservation.api.dto.DriverLocationRequest;
import com.vora.reservation.application.exception.ForbiddenOperationException;
import com.vora.reservation.application.exception.ReservationNotAcceptedException;
import com.vora.reservation.application.exception.ReservationNotStartedException;
import com.vora.reservation.application.exception.ReservationNotFoundException;
import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.enums.TurnStatus;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.domain.model.Turn;
import com.vora.reservation.infrastructure.persistence.ReservationRepository;
import com.vora.reservation.infrastructure.persistence.TurnRepository;
import com.vora.reservation.infrastructure.realtime.DriverLocationPublisher;
import com.vora.reservation.infrastructure.security.AuthenticatedUser;
import com.vora.reservation.infrastructure.security.GatewayHeaderAuthenticationFilter;
import com.vora.reservation.infrastructure.security.SecurityConfig;
import com.vora.reservation.infrastructure.security.VoraRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DriverLocationController.class)
@Import({SecurityConfig.class})
class DriverLocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservationRepository reservationRepository;

    @MockBean
    private TurnRepository turnRepository;

    @MockBean
    private DriverLocationPublisher locationPublisher;

    private AuthenticatedUser driver;
    private UUID reservationId;
    private UUID turnId;

    @BeforeEach
    void setUp() {
        driver = new AuthenticatedUser(3L, VoraRole.CHAUFFEUR, 42L);
        reservationId = UUID.randomUUID();
        turnId = UUID.randomUUID();
    }

    private Reservation enCourseReservation() {
        Turn turn = Turn.open(42L, UUID.randomUUID(), 4);
        turn.setId(turnId);

        Reservation reservation = Reservation.create(
                7L,
                new BigDecimal("3.866700"),
                new BigDecimal("11.516700"),
                "Devant la pharmacie",
                new BigDecimal("3.883300"),
                new BigDecimal("11.516700"),
                "Bastos, Yaoundé",
                new BigDecimal("1000.00"),
                PaymentMethod.MTN_MOMO);
        reservation.setId(reservationId);
        reservation.assignToTurn(turn, 0);
        reservation.start();
        return reservation;
    }

    @Test
    void shouldPublishLocationForDriverTurnAndEnCoursReservation() throws Exception {
        Reservation reservation = enCourseReservation();
        when(reservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(reservation));

        DriverLocationRequest request = new DriverLocationRequest(
                reservationId,
                new BigDecimal("3.8701"),
                new BigDecimal("11.5203"),
                "Navigo 3m");

        mockMvc.perform(post("/api/v1/driver/reservations/{id}/location", reservationId)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "3")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CHAUFFEUR")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_DRIVER_ID, "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(locationPublisher).publish(
                eq(reservation),
                eq(3.8701),
                eq(11.5203),
                anyString());
    }

    @Test
    void shouldRejectWhenNoDriverHeader() throws Exception {
        DriverLocationRequest request = new DriverLocationRequest(
                reservationId,
                new BigDecimal("3.8701"),
                new BigDecimal("11.5203"),
                "Navigo 3m");

        mockMvc.perform(post("/api/v1/driver/reservations/{id}/location", reservationId)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "3")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CHAUFFEUR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectWhenNotDriverRole() throws Exception {
        DriverLocationRequest request = new DriverLocationRequest(
                reservationId,
                new BigDecimal("3.8701"),
                new BigDecimal("11.5203"),
                "Navigo 3m");

        mockMvc.perform(post("/api/v1/driver/reservations/{id}/location", reservationId)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectWhenReservationNotFound() throws Exception {
        when(reservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.empty());

        DriverLocationRequest request = new DriverLocationRequest(
                reservationId,
                new BigDecimal("3.8701"),
                new BigDecimal("11.5203"),
                "Navigo 3m");

        mockMvc.perform(post("/api/v1/driver/reservations/{id}/location", reservationId)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "3")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CHAUFFEUR")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_DRIVER_ID, "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectWhenReservationNotStarted() throws Exception {
        Reservation notStarted = enCourseReservation();
        notStarted.setStatus(ReservationStatus.ACCEPTEE);
        when(reservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(notStarted));

        DriverLocationRequest request = new DriverLocationRequest(
                reservationId,
                new BigDecimal("3.8701"),
                new BigDecimal("11.5203"),
                "Navigo 3m");

        mockMvc.perform(post("/api/v1/driver/reservations/{id}/location", reservationId)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "3")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CHAUFFEUR")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_DRIVER_ID, "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectWhenDriverNotAssignedToReservation() throws Exception {
        Turn otherTurn = Turn.open(99L, UUID.randomUUID(), 4);
        otherTurn.setId(UUID.randomUUID());

        Reservation otherDriverReservation = Reservation.create(
                7L,
                new BigDecimal("3.866700"),
                new BigDecimal("11.516700"),
                "Devant la pharmacie",
                new BigDecimal("3.883300"),
                new BigDecimal("11.516700"),
                "Bastos, Yaoundé",
                new BigDecimal("1000.00"),
                PaymentMethod.MTN_MOMO);
        otherDriverReservation.setId(reservationId);
        otherDriverReservation.assignToTurn(otherTurn, 0);
        otherDriverReservation.start();

        when(reservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(otherDriverReservation));

        DriverLocationRequest request = new DriverLocationRequest(
                reservationId,
                new BigDecimal("3.8701"),
                new BigDecimal("11.5203"),
                "Navigo 3m");

        mockMvc.perform(post("/api/v1/driver/reservations/{id}/location", reservationId)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "3")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CHAUFFEUR")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_DRIVER_ID, "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectWhenInvalidPayload() throws Exception {
        String invalidBody = """
                {
                  "reservationId": "00000000-0000-0000-0000-000000000000",
                  "latitude": 200,
                  "longitude": 11.5167,
                  "precision": "Navigo 3m"
                }
                """;

        mockMvc.perform(post("/api/v1/driver/reservations/{id}/location", reservationId)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "3")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CHAUFFEUR")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_DRIVER_ID, "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }
}
