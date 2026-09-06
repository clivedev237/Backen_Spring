package com.vora.reservation.application.service;

import com.vora.reservation.application.exception.PaymentAlreadyInitiatedException;
import com.vora.reservation.application.exception.PaymentNotInitiatedException;
import com.vora.reservation.application.exception.PaymentUnavailableException;
import com.vora.reservation.application.exception.ReservationNotFoundException;
import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.enums.PaymentRefStatus;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.model.PaymentReference;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.infrastructure.client.payment.PaymentClient;
import com.vora.reservation.infrastructure.client.payment.dto.InitiatePaymentResponse;
import com.vora.reservation.infrastructure.client.payment.dto.PaymentStatusResponse;
import com.vora.reservation.infrastructure.persistence.PaymentReferenceRepository;
import com.vora.reservation.infrastructure.persistence.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final String EXTERNAL_PAYMENT_ID = "node_payment_123";

    @Mock private PaymentClient paymentClient;
    @Mock private PaymentReferenceRepository paymentReferenceRepository;
    @Mock private ReservationRepository reservationRepository;

    @InjectMocks private PaymentService paymentService;

    private PaymentReference paymentReference;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        paymentReference = new PaymentReference();
        paymentReference.setId(UUID.randomUUID());
        paymentReference.setPaymentId(EXTERNAL_PAYMENT_ID);
        paymentReference.setAmount(new BigDecimal("1000.00"));
        paymentReference.setMethod(PaymentMethod.MTN_MOMO);
        paymentReference.setStatus(PaymentRefStatus.EN_ATTENTE);
        paymentReference.setUpdatedAt(OffsetDateTime.now());

        reservation = Reservation.create(7L,
                new BigDecimal("3.866700"), new BigDecimal("11.516700"), null,
                new BigDecimal("3.883300"), new BigDecimal("11.516700"),
                "Bastos, Yaoundé", new BigDecimal("1000.00"), PaymentMethod.MTN_MOMO);
        reservation.setId(RESERVATION_ID);
        reservation.setStatus(ReservationStatus.ARRIVEE_CONFIRMEE);
        reservation.setPaymentReference(paymentReference);

        lenient().when(reservationRepository.findByIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(reservation));
        lenient().when(paymentReferenceRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(paymentReference));
        lenient().when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentReferenceRepository.save(any(PaymentReference.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // --- Initiate ---

    @Test
    void shouldInitiatePaymentWhenArrived() {
        InitiatePaymentResponse nodeResponse = new InitiatePaymentResponse(
                EXTERNAL_PAYMENT_ID, "token_xyz", "EN_ATTENTE", OffsetDateTime.now(), "1000.00", "MTN_MOMO");
        when(paymentClient.initiate(any())).thenReturn(nodeResponse);

        InitiatePaymentResponse result = paymentService.initiatePayment(RESERVATION_ID);

        assertThat(result.getId()).isEqualTo(EXTERNAL_PAYMENT_ID);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAIEMENT_EN_COURS);
    }

    @Test
    void shouldThrowWhenAlreadyInitiated() {
        paymentReference.setStatus(PaymentRefStatus.REUSSI);

        assertThatThrownBy(() -> paymentService.initiatePayment(RESERVATION_ID))
                .isInstanceOf(PaymentAlreadyInitiatedException.class)
                .hasMessageContaining("PAIEMENT_DEJA_INITIE");
    }

    @Test
    void shouldThrowWhenNotArrived() {
        reservation.setStatus(ReservationStatus.EN_COURS);

        assertThatThrownBy(() -> paymentService.initiatePayment(RESERVATION_ID))
                .isInstanceOf(PaymentNotInitiatedException.class)
                .hasMessageContaining("PAIEMENT_NON_INITIABLE");
    }

    @Test
    void shouldThrowPaymentUnavailableWhenNodeDown() {
        when(paymentClient.initiate(any()))
                .thenThrow(new PaymentUnavailableException("Node unreachable"));

        assertThatThrownBy(() -> paymentService.initiatePayment(RESERVATION_ID))
                .isInstanceOf(PaymentUnavailableException.class)
                .hasMessageContaining("Node unreachable");
    }

    // --- Confirm cash ---

    @Test
    void shouldConfirmCashWhenAlreadySucceeded() {
        paymentReference.setStatus(PaymentRefStatus.REUSSI);
        reservation.setStatus(ReservationStatus.PAIEMENT_EN_COURS);

        PaymentStatusResponse result = paymentService.confirmCashPayment(RESERVATION_ID);

        assertThat(result).isNotNull();
        // Si déjà REUSSI, confirmCashPayment ne refait pas la transition.
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAIEMENT_EN_COURS);
    }

    // --- Query status ---

    @Test
    void shouldQueryStatusFromNode() {
        PaymentStatusResponse nodeStatus = new PaymentStatusResponse(
                EXTERNAL_PAYMENT_ID, "token_xyz", "PAID", "1000.00", "MTN_MOMO", OffsetDateTime.now());
        when(paymentClient.getStatus(EXTERNAL_PAYMENT_ID)).thenReturn(nodeStatus);

        PaymentStatusResponse result = paymentService.queryStatus(RESERVATION_ID);

        assertThat(result.getStatus()).isEqualTo("PAID");
    }
}
