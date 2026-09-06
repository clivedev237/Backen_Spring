package com.vora.reservation.api;

import com.vora.reservation.api.controller.ReservationController;
import com.vora.reservation.application.service.DriveTripService;
import com.vora.reservation.application.service.PaymentService;
import com.vora.reservation.application.service.ReservationService;
import com.vora.reservation.application.exception.PaymentAlreadyInitiatedException;
import com.vora.reservation.application.exception.PaymentNotInitiatedException;
import com.vora.reservation.application.exception.PaymentUnavailableException;
import com.vora.reservation.application.exception.ReservationNotFoundException;
import com.vora.reservation.infrastructure.security.GatewayHeaderAuthenticationFilter;
import com.vora.reservation.infrastructure.security.SecurityConfig;
import com.vora.reservation.api.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReservationController.class)
@Import({SecurityConfig.class, WebConfig.class})
class ReservationControllerPaymentTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ReservationService reservationService;
    @MockBean private DriveTripService driveTripService;
    @MockBean private PaymentService paymentService;

    private static final String CLIENT_HEADERS =
            GatewayHeaderAuthenticationFilter.HEADER_USER_ID + ":7:" +
            GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE + ":CLIENT";

    @Test
    void initiatePaymentShouldReturn200ForArrivedReservation() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.initiatePayment(eq(id)))
                .thenReturn(new com.vora.reservation.infrastructure.client.payment.dto
                        .InitiatePaymentResponse("node_id", "token", "EN_ATTENTE", null, "1000.00", "MTN_MOMO"));

        mockMvc.perform(post("/api/v1/reservations/{id}/payment", id)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("node_id"))
                .andExpect(jsonPath("$.status").value("EN_ATTENTE"));
    }

    @Test
    void initiatePaymentShouldReturn404WhenReservationNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.initiatePayment(eq(id)))
                .thenThrow(new ReservationNotFoundException(id));

        mockMvc.perform(post("/api/v1/reservations/{id}/payment", id)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void initiatePaymentShouldReturn409WhenAlreadyInitiated() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.initiatePayment(eq(id)))
                .thenThrow(new PaymentAlreadyInitiatedException("PAIEMENT_DEJA_INITIE : ..."));

        mockMvc.perform(post("/api/v1/reservations/{id}/payment", id)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PAIEMENT_DEJA_INITIE : ..."));
    }

    @Test
    void initiatePaymentShouldReturn400WhenNotInInitiableState() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.initiatePayment(eq(id)))
                .thenThrow(new PaymentNotInitiatedException("PAIEMENT_NON_INITIABLE : ..."));

        mockMvc.perform(post("/api/v1/reservations/{id}/payment", id)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PAIEMENT_NON_INITIABLE : ..."));
    }

    @Test
    void initiatePaymentShouldReturn503WhenPaymentUnavailable() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.initiatePayment(eq(id)))
                .thenThrow(new PaymentUnavailableException("Service de paiement indisponible", new RuntimeException("upstream")));

        mockMvc.perform(post("/api/v1/reservations/{id}/payment", id)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getPaymentStatusShouldReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.queryStatus(eq(id)))
                .thenReturn(new com.vora.reservation.infrastructure.client.payment.dto
                        .PaymentStatusResponse("node_id", "token", "PAID", "1000.00", "MTN_MOMO", null));

        mockMvc.perform(get("/api/v1/reservations/{id}/payment", id)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("node_id"))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void getPaymentStatusShouldReturn400WhenPaymentNotInitiated() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.queryStatus(eq(id)))
                .thenThrow(new PaymentNotInitiatedException("PAIEMENT_NON_INITIE : ..."));

        mockMvc.perform(get("/api/v1/reservations/{id}/payment", id)
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ID, "7")
                        .header(GatewayHeaderAuthenticationFilter.HEADER_USER_ROLE, "CLIENT"))
                .andExpect(status().isBadRequest());
    }
}
