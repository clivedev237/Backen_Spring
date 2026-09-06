package com.vora.reservation.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vora.reservation.api.controller.ReservationController;
import com.vora.reservation.application.service.DriveTripService;
import com.vora.reservation.application.service.ReservationService;
import com.vora.reservation.application.exception.ForbiddenOperationException;
import com.vora.reservation.application.exception.ReservationNotFoundException;
import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.infrastructure.security.GatewayHeaderAuthenticationFilter;
import com.vora.reservation.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import com.vora.reservation.application.exception.DestinationOutOfCorridorException;
import com.vora.reservation.application.exception.GeoServiceUnavailableException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(controllers = ReservationController.class)
@Import({SecurityConfig.class, WebConfig.class})
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private DriveTripService driveTripService;

    private Reservation sampleReservation() {
        return Reservation.create(7L,
                new BigDecimal("3.866700"), new BigDecimal("11.516700"), "Devant la pharmacie",
                new BigDecimal("3.883300"), new BigDecimal("11.516700"), "Bastos, Yaoundé",
                new BigDecimal("1000.00"), PaymentMethod.MTN_MOMO);
    }

    @Test
    void createShouldReturn201ForAuthenticatedClient() throws Exception {
        when(reservationService.create(any(), any())).thenReturn(sampleReservation());

        String body = """
                {
                  "pickup": {"latitude": 3.8667, "longitude": 11.5167, "precision": "Devant la pharmacie"},
                  "destination": {"address": "Bastos, Yaoundé", "latitude": 3.8833, "longitude": 11.5167},
                  "proposedPrice": 1000,
                  "paymentMethod": "MTN_MOMO"
                }
                """;

        mockMvc.perform(post("/api/v1/reservations")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.clientId").value(7))
                .andExpect(jsonPath("$.status").value("EN_ATTENTE"));
    }

    @Test
    void createShouldReturn400ForInvalidPayload() throws Exception {
        String invalidBody = """
                {
                  "pickup": {"latitude": 200, "longitude": 11.5167},
                  "destination": {"address": "", "latitude": 3.8833, "longitude": 11.5167},
                  "proposedPrice": -5,
                  "paymentMethod": "MTN_MOMO"
                }
                """;

        mockMvc.perform(post("/api/v1/reservations")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT")
                        .contentType("application/json")
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void createShouldReturn403WithoutIdentityHeaders() throws Exception {
        // Aucun AuthenticationEntryPoint custom n'est configuré (pas de httpBasic/formLogin) :
        // le comportement par défaut de Spring Security pour une requête non authentifiée
        // sur un endpoint protégé est ici 403, pas 401.
        String body = """
                {
                  "pickup": {"latitude": 3.8667, "longitude": 11.5167},
                  "destination": {"address": "Bastos", "latitude": 3.8833, "longitude": 11.5167},
                  "proposedPrice": 1000,
                  "paymentMethod": "MTN_MOMO"
                }
                """;

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }
    @Test
    void createShouldReturn422WhenDestinationOutOfCorridor() throws Exception {
        when(reservationService.create(any(), any())).thenThrow(
                new DestinationOutOfCorridorException("Destination hors de la zone de couverture VORA."));

        String body = """
                {
                  "pickup": {"latitude": 3.8667, "longitude": 11.5167},
                  "destination": {"address": "Hors zone", "latitude": 0.0, "longitude": 0.0},
                  "proposedPrice": 1000,
                  "paymentMethod": "MTN_MOMO"
                }
                """;

        mockMvc.perform(post("/api/v1/reservations")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createShouldReturn503WhenGeoServiceUnavailable() throws Exception {
        when(reservationService.create(any(), any())).thenThrow(
                new GeoServiceUnavailableException("Django Geo indisponible", new RuntimeException("timeout")));

        String body = """
                {
                  "pickup": {"latitude": 3.8667, "longitude": 11.5167},
                  "destination": {"address": "Bastos", "latitude": 3.8833, "longitude": 11.5167},
                  "proposedPrice": 1000,
                  "paymentMethod": "MTN_MOMO"
                }
                """;

        mockMvc.perform(post("/api/v1/reservations")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getByIdShouldReturn404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(reservationService.getById(any(), eq(id))).thenThrow(new ReservationNotFoundException(id));

        mockMvc.perform(get("/api/v1/reservations/{id}", id)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByIdShouldReturn403WhenNotOwner() throws Exception {
        UUID id = UUID.randomUUID();
        when(reservationService.getById(any(), eq(id)))
                .thenThrow(new ForbiddenOperationException("Vous ne pouvez consulter que vos propres réservations."));

        mockMvc.perform(get("/api/v1/reservations/{id}", id)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "8")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listShouldReturnPagedReservations() throws Exception {
        when(reservationService.list(any(), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleReservation())));

        mockMvc.perform(get("/api/v1/reservations")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].clientId").value(7));
    }

    @Test
    void arrivalShouldReturn200ForOwner() throws Exception {
        UUID id = UUID.randomUUID();
        Reservation arrived = sampleReservation();
        arrived.setStatus(com.vora.reservation.domain.enums.ReservationStatus.ARRIVEE_CONFIRMEE);
        when(driveTripService.confirmArrival(any(), eq(id))).thenReturn(arrived);

        mockMvc.perform(post("/api/v1/reservations/{id}/arrival", id)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVEE_CONFIRMEE"));
    }

    @Test
    void arrivalShouldReturn403WhenNotOwner() throws Exception {
        UUID id = UUID.randomUUID();
        when(driveTripService.confirmArrival(any(), eq(id))).thenThrow(
                new ForbiddenOperationException("Vous ne pouvez confirmer que vos propres réservations."));

        mockMvc.perform(post("/api/v1/reservations/{id}/arrival", id)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "8")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isForbidden());
    }

}
