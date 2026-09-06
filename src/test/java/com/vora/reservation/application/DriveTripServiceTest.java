package com.vora.reservation.application;

import com.vora.reservation.application.exception.ForbiddenOperationException;
import com.vora.reservation.application.exception.OfferAlreadyResolvedException;
import com.vora.reservation.application.exception.OfferNotFoundException;
import com.vora.reservation.application.exception.OfferNotAddressedToThisDriverException;
import com.vora.reservation.application.exception.ReservationNotAcceptedException;
import com.vora.reservation.application.exception.ReservationNotStartedException;
import com.vora.reservation.application.exception.ReservationNotFoundException;
import com.vora.reservation.application.exception.TurnNotFoundException;
import com.vora.reservation.application.service.DriveTripService;
import com.vora.reservation.application.service.OfferService;
import com.vora.reservation.application.service.PaymentService;
import com.vora.reservation.domain.model.PaymentReference;
import com.vora.reservation.domain.enums.OfferStatus;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.enums.TurnStatus;
import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.domain.model.ReservationOffer;
import com.vora.reservation.domain.model.Turn;
import com.vora.reservation.infrastructure.persistence.ReservationOfferRepository;
import com.vora.reservation.infrastructure.persistence.ReservationRepository;
import com.vora.reservation.infrastructure.persistence.TurnRepository;
import com.vora.reservation.infrastructure.security.AuthenticatedUser;
import com.vora.reservation.infrastructure.security.VoraRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriveTripServiceTest {

    @Mock
    private TurnRepository turnRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationOfferRepository reservationOfferRepository;
    @Mock
    private OfferService offerService;
    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private DriveTripService driveTripService;

    private static final Long DRIVER_ID = 42L;
    private AuthenticatedUser driver;
    private AuthenticatedUser client;

    @BeforeEach
    void setUp() {
        driver = new AuthenticatedUser(3L, VoraRole.CHAUFFEUR, DRIVER_ID);
        client = new AuthenticatedUser(7L, VoraRole.CLIENT, null);

        lenient().when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(turnRepository.save(any(Turn.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(reservationOfferRepository.save(any(ReservationOffer.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentService.initiatePayment(any(UUID.class))).thenAnswer(inv -> null);
    }

    private Reservation reservationWithTurn(ReservationStatus status) {
        Turn turn = Turn.open(DRIVER_ID, UUID.randomUUID(), 4);
        turn.setId(UUID.randomUUID());
        turn.incrementLoad(); // currentLoad = 1
        Reservation reservation = Reservation.create(7L,
                new BigDecimal("3.866700"), new BigDecimal("11.516700"), null,
                new BigDecimal("3.883300"), new BigDecimal("11.516700"), "Bastos, Yaoundé",
                new BigDecimal("1000.00"), PaymentMethod.MTN_MOMO);
        reservation.setId(UUID.randomUUID());
        reservation.assignToTurn(turn, 0);
        if (status != null) {
            reservation.setStatus(status);
        } else {
            reservation.setStatus(ReservationStatus.ACCEPTEE);
        }
        lenient().when(reservationRepository.findByIdForUpdate(reservation.getId()))
                .thenReturn(Optional.of(reservation));
        return reservation;
    }

    private void mockOfferAccepted(UUID reservationId, Long driverId) {
        ReservationOffer offer = ReservationOffer.sendTo(
                Reservation.create(7L,
                        new BigDecimal("3.866700"), new BigDecimal("11.516700"), null,
                        new BigDecimal("3.883300"), new BigDecimal("11.516700"), "Bastos, Yaoundé",
                        new BigDecimal("1000.00"), PaymentMethod.MTN_MOMO),
                driverId);
        offer.setId(UUID.randomUUID());
        offer.setStatus(OfferStatus.ACCEPTEE);
        lenient().when(reservationOfferRepository.findByReservationIdAndStatus(reservationId, OfferStatus.ACCEPTEE))
                .thenReturn(List.of(offer));
    }

    // --- Start --------------------------------------------------------------------

    @Test
    void shouldStartAcceptedReservationForAssignedDriver() {
        Reservation reservation = reservationWithTurn(ReservationStatus.ACCEPTEE);
        mockOfferAccepted(reservation.getId(), DRIVER_ID);

        Reservation result = driveTripService.startTrip(driver, reservation.getId());

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.EN_COURS);
        assertThat(result.getBoardedAt()).isNotNull();
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void shouldBeIdempotentWhenAlreadyStarted() {
        Reservation reservation = reservationWithTurn(ReservationStatus.EN_COURS);
        mockOfferAccepted(reservation.getId(), DRIVER_ID);

        Reservation result = driveTripService.startTrip(driver, reservation.getId());

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.EN_COURS);
    }

    @Test
    void shouldRejectStartWhenNoTurn() {
        Reservation reservation = Reservation.create(7L,
                new BigDecimal("3.866700"), new BigDecimal("11.516700"), null,
                new BigDecimal("3.883300"), new BigDecimal("11.516700"), "Bastos, Yaoundé",
                new BigDecimal("1000.00"), PaymentMethod.MTN_MOMO);
        reservation.setId(UUID.randomUUID());
        reservation.setStatus(ReservationStatus.ACCEPTEE);

        when(reservationRepository.findByIdForUpdate(reservation.getId()))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> driveTripService.startTrip(driver, reservation.getId()))
                .isInstanceOf(ReservationNotAcceptedException.class)
                .hasMessageContaining("COURSE_NON_ATTRIBUEE");
    }

    @Test
    void shouldRejectStartWhenDriverNotAssigned() {
        Reservation reservation = reservationWithTurn(ReservationStatus.ACCEPTEE);
        mockOfferAccepted(reservation.getId(), 99L);

        assertThatThrownBy(() -> driveTripService.startTrip(driver, reservation.getId()))
                .isInstanceOf(OfferNotAddressedToThisDriverException.class);
    }

    @Test
    void shouldRejectStartWhenOfferNotFound() {
        Reservation reservation = reservationWithTurn(ReservationStatus.ACCEPTEE);
        when(reservationOfferRepository.findByReservationIdAndStatus(reservation.getId(), OfferStatus.ACCEPTEE))
                .thenReturn(List.of());

        assertThatThrownBy(() -> driveTripService.startTrip(driver, reservation.getId()))
                .isInstanceOf(OfferNotFoundException.class);
    }

    @Test
    void shouldRejectStartWhenNotDriverRole() {
        AuthenticatedUser client = new AuthenticatedUser(7L, VoraRole.CLIENT, null);

        assertThatThrownBy(() -> driveTripService.startTrip(client, UUID.randomUUID()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    // --- Arrival -----------------------------------------------------------------

    @Test
    void shouldConfirmArrivalForOwnerAndReleaseSeat() {
        Reservation reservation = reservationWithTurn(ReservationStatus.EN_COURS);
        reservation.setPaymentReference(new PaymentReference());

        Reservation result = driveTripService.confirmArrival(client, reservation.getId());

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.ARRIVEE_CONFIRMEE);
        assertThat(result.getArrivedAt()).isNotNull();
        verify(turnRepository).save(any(Turn.class));
        verify(paymentService).initiatePayment(reservation.getId());
    }

    @Test
    void shouldRejectArrivalWhenNotOwner() {
        Reservation reservation = reservationWithTurn(ReservationStatus.EN_COURS);

        AuthenticatedUser otherClient = new AuthenticatedUser(8L, VoraRole.CLIENT, null);

        assertThatThrownBy(() -> driveTripService.confirmArrival(otherClient, reservation.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void shouldRejectArrivalWhenNotStarted() {
        Reservation reservation = reservationWithTurn(ReservationStatus.ACCEPTEE);

        assertThatThrownBy(() -> driveTripService.confirmArrival(client, reservation.getId()))
                .isInstanceOf(ReservationNotStartedException.class)
                .hasMessageContaining("COURSE_NON_DEMARREE");
    }

    @Test
    void shouldRejectArrivalWhenNonClientRole() {
        driver = new AuthenticatedUser(3L, VoraRole.CHAUFFEUR, DRIVER_ID);

        Reservation reservation = reservationWithTurn(ReservationStatus.EN_COURS);

        assertThatThrownBy(() -> driveTripService.confirmArrival(driver, reservation.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    // --- Relance matching --------------------------------------------------------

    @Test
    void shouldRelaunchMatchingWhenTurnHasFreeSeatAndPendingCandidates() {
        Turn turn = Turn.open(DRIVER_ID, UUID.randomUUID(), 4);
        turn.setId(UUID.randomUUID());
        turn.incrementLoad(); // currentLoad = 1

        Reservation pendingCandidate = Reservation.create(9L,
                new BigDecimal("3.866700"), new BigDecimal("11.516700"), null,
                new BigDecimal("3.883300"), new BigDecimal("11.516700"), "Bastos, Yaoundé",
                new BigDecimal("1200.00"), PaymentMethod.MTN_MOMO);
        pendingCandidate.setId(UUID.randomUUID());

        when(turnRepository.findByDriverIdAndStatusInForUpdate(
                eq(DRIVER_ID), anyList()))
                .thenReturn(Optional.of(turn));
        when(reservationRepository.findByStatus(ReservationStatus.EN_ATTENTE))
                .thenReturn(List.of(pendingCandidate));

        Reservation reservation = reservationWithTurn(ReservationStatus.EN_COURS);
        mockOfferAccepted(reservation.getId(), DRIVER_ID);

        driveTripService.confirmArrival(client, reservation.getId());

        verify(offerService).diffuseOffers(eq(pendingCandidate), anyList());
    }
}
