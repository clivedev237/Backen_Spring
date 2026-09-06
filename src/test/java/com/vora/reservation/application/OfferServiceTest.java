package com.vora.reservation.application;

import com.vora.reservation.application.exception.ForbiddenOperationException;
import com.vora.reservation.application.exception.OfferAlreadyResolvedException;
import com.vora.reservation.application.exception.OfferExpiredException;
import com.vora.reservation.application.exception.TurnFullException;
import com.vora.reservation.application.service.OfferService;
import com.vora.reservation.domain.enums.OfferStatus;
import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.enums.TurnStatus;
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

import com.vora.reservation.infrastructure.notification.NotificationService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OfferServiceTest {
    @Mock
    private ReservationOfferRepository offerRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private TurnRepository turnRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OfferService offerService;

    private static final Long DRIVER_ID = 42L;
    private AuthenticatedUser driver;
    private Reservation reservation;
    private UUID reservationId;

    @BeforeEach
    void setUp() {
        driver = new AuthenticatedUser(3L, VoraRole.CHAUFFEUR, DRIVER_ID);

        reservation = Reservation.create(7L,
                new BigDecimal("3.866700"), new BigDecimal("11.516700"), "Devant la pharmacie",
                new BigDecimal("3.883300"), new BigDecimal("11.516700"), "Bastos, Yaoundé",
                new BigDecimal("1000.00"), PaymentMethod.MTN_MOMO);
        reservationId = UUID.randomUUID();
        reservation.setId(reservationId);
        reservation.diffuse(); // simule le passage EN_ATTENTE -> DIFFUSEE

        lenient().when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(offerRepository.save(any(ReservationOffer.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(turnRepository.save(any(Turn.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ReservationOffer offerFor(Reservation r, Long driverId) {
        ReservationOffer offer = ReservationOffer.sendTo(r, driverId);
        offer.setId(UUID.randomUUID());
        return offer;
    }

    // --- diffuseOffers ---------------------------------------------------

    @Test
    void shouldDiffuseOffersToAllCandidatesAndMarkReservationDiffusee() {
        Reservation fresh = Reservation.create(7L,
                new BigDecimal("3.866700"), new BigDecimal("11.516700"), null,
                new BigDecimal("3.883300"), new BigDecimal("11.516700"), "Bastos, Yaoundé",
                new BigDecimal("1000.00"), PaymentMethod.MTN_MOMO);

        List<ReservationOffer> offers = offerService.diffuseOffers(fresh, List.of(10L, 20L));

        assertThat(offers).hasSize(2);
        assertThat(fresh.getStatus()).isEqualTo(ReservationStatus.DIFFUSEE);
        verify(reservationRepository).save(fresh);
    }

    @Test
    void shouldDoNothingWhenNoCandidateDrivers() {
        Reservation fresh = Reservation.create(7L,
                new BigDecimal("3.866700"), new BigDecimal("11.516700"), null,
                new BigDecimal("3.883300"), new BigDecimal("11.516700"), "Bastos, Yaoundé",
                new BigDecimal("1000.00"), PaymentMethod.MTN_MOMO);

        List<ReservationOffer> offers = offerService.diffuseOffers(fresh, List.of());

        assertThat(offers).isEmpty();
        assertThat(fresh.getStatus()).isEqualTo(ReservationStatus.EN_ATTENTE);
    }

    // --- acceptOffer : chemins nominaux -----------------------------------

    @Test
    void shouldAcceptOfferAndCreateNewTurnWhenNoneActive() {
        ReservationOffer offer = offerFor(reservation, DRIVER_ID);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(turnRepository.findByDriverIdAndStatusIn(DRIVER_ID,
                List.of(TurnStatus.OUVERT, TurnStatus.COMPLET, TurnStatus.EN_COURS)))
                .thenReturn(List.of());
        when(offerRepository.findByReservationIdAndStatus(reservationId, OfferStatus.EN_ATTENTE))
                .thenReturn(List.of(offer));

        Reservation result = offerService.acceptOffer(driver, offer.getId());

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.ACCEPTEE);
        assertThat(result.getSequenceIndex()).isEqualTo(0);
        assertThat(result.getTurn()).isNotNull();
        assertThat(result.getTurn().getDriverId()).isEqualTo(DRIVER_ID);
        assertThat(result.getTurn().getCurrentLoad()).isEqualTo(1);
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTEE);
    }

    @Test
    void shouldInsertIntoExistingOpenTurnAtCurrentLoadPosition() {
        Turn existingTurn = Turn.open(DRIVER_ID, UUID.randomUUID(), 4);
        existingTurn.setId(UUID.randomUUID());
        existingTurn.incrementLoad();
        existingTurn.incrementLoad(); // current_load = 2

        ReservationOffer offer = offerFor(reservation, DRIVER_ID);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(turnRepository.findByDriverIdAndStatusIn(DRIVER_ID,
                List.of(TurnStatus.OUVERT, TurnStatus.COMPLET, TurnStatus.EN_COURS)))
                .thenReturn(List.of(existingTurn));
        when(turnRepository.findByIdForUpdate(existingTurn.getId())).thenReturn(Optional.of(existingTurn));
        when(offerRepository.findByReservationIdAndStatus(reservationId, OfferStatus.EN_ATTENTE))
                .thenReturn(List.of(offer));

        Reservation result = offerService.acceptOffer(driver, offer.getId());

        assertThat(result.getSequenceIndex()).isEqualTo(2);
        assertThat(result.getTurn()).isSameAs(existingTurn);
        assertThat(existingTurn.getCurrentLoad()).isEqualTo(3);
    }

    @Test
    void shouldInvalidateOtherPendingOffersForTheSameReservationOnAcceptance() {
        ReservationOffer winning = offerFor(reservation, DRIVER_ID);
        ReservationOffer other = offerFor(reservation, 99L);

        when(offerRepository.findById(winning.getId())).thenReturn(Optional.of(winning));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(turnRepository.findByDriverIdAndStatusIn(DRIVER_ID,
                List.of(TurnStatus.OUVERT, TurnStatus.COMPLET, TurnStatus.EN_COURS)))
                .thenReturn(List.of());
        when(offerRepository.findByReservationIdAndStatus(reservationId, OfferStatus.EN_ATTENTE))
                .thenReturn(List.of(winning, other));

        offerService.acceptOffer(driver, winning.getId());

        assertThat(other.getStatus()).isEqualTo(OfferStatus.INVALIDEE);
        assertThat(winning.getStatus()).isEqualTo(OfferStatus.ACCEPTEE);
    }

    // --- acceptOffer : concurrence et erreurs métier ----------------------

    @Test
    void shouldRejectSecondAcceptanceOnceReservationAlreadyAccepted() {
        Turn winningTurn = Turn.open(11L, null, 4);
        reservation.assignToTurn(winningTurn, 0); // simule qu'un autre chauffeur a déjà gagné

        ReservationOffer lateOffer = offerFor(reservation, DRIVER_ID);
        when(offerRepository.findById(lateOffer.getId())).thenReturn(Optional.of(lateOffer));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> offerService.acceptOffer(driver, lateOffer.getId()))
                .isInstanceOf(OfferAlreadyResolvedException.class)
                .hasMessageContaining("COURSE_DEJA_ATTRIBUEE");
    }

    @Test
    void shouldRejectAcceptanceWhenOfferAlreadyInvalidated() {
        ReservationOffer offer = offerFor(reservation, DRIVER_ID);
        offer.invalidate();

        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> offerService.acceptOffer(driver, offer.getId()))
                .isInstanceOf(OfferAlreadyResolvedException.class);
    }

    @Test
    void shouldRejectAndExpireOfferWhenValidityWindowPassed() {
        ReservationOffer offer = offerFor(reservation, DRIVER_ID);
        offer.setSentAt(OffsetDateTime.now().minusMinutes(10));
        offer.setExpiredAt(OffsetDateTime.now().minusMinutes(5));

        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> offerService.acceptOffer(driver, offer.getId()))
                .isInstanceOf(OfferExpiredException.class);

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.EXPIREE);
    }

    @Test
    void shouldRejectAcceptanceAndKeepOfferPendingWhenTurnIsFull() {
        Turn fullTurn = Turn.open(DRIVER_ID, null, 4);
        fullTurn.setId(UUID.randomUUID());
        fullTurn.incrementLoad();
        fullTurn.incrementLoad();
        fullTurn.incrementLoad();
        fullTurn.incrementLoad(); // current_load = 4, COMPLET

        ReservationOffer offer = offerFor(reservation, DRIVER_ID);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(turnRepository.findByDriverIdAndStatusIn(DRIVER_ID,
                List.of(TurnStatus.OUVERT, TurnStatus.COMPLET, TurnStatus.EN_COURS)))
                .thenReturn(List.of(fullTurn));
        when(turnRepository.findByIdForUpdate(fullTurn.getId())).thenReturn(Optional.of(fullTurn));

        assertThatThrownBy(() -> offerService.acceptOffer(driver, offer.getId()))
                .isInstanceOf(TurnFullException.class);

        // Décision de cadrage Phase 5 : l'offre reste EN_ATTENTE, pas d'invalidation.
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.EN_ATTENTE);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.DIFFUSEE);
    }

    @Test
    void shouldRejectAcceptanceWhenOfferNotAddressedToThisDriver() {
        ReservationOffer offer = offerFor(reservation, 999L);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> offerService.acceptOffer(driver, offer.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void shouldRejectAcceptanceByNonDriverRole() {
        AuthenticatedUser client = new AuthenticatedUser(7L, VoraRole.CLIENT, null);

        assertThatThrownBy(() -> offerService.acceptOffer(client, UUID.randomUUID()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    // --- declineOffer ------------------------------------------------------

    @Test
    void shouldDeclineOfferWithoutTouchingReservation() {
        ReservationOffer offer = offerFor(reservation, DRIVER_ID);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        offerService.declineOffer(driver, offer.getId());

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.REFUSEE);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.DIFFUSEE);
    }

    @Test
    void shouldRejectDeclineWhenOfferAlreadyResolved() {
        ReservationOffer offer = offerFor(reservation, DRIVER_ID);
        offer.accept();
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> offerService.declineOffer(driver, offer.getId()))
                .isInstanceOf(OfferAlreadyResolvedException.class);
    }

    // --- listPendingOffersForDriver -----------------------------------------

    @Test
    void shouldLazilyExpireOverdueOffersAndExcludeThemFromListing() {
        ReservationOffer stillValid = offerFor(reservation, DRIVER_ID);

        ReservationOffer overdue = offerFor(reservation, DRIVER_ID);
        overdue.setSentAt(OffsetDateTime.now().minusMinutes(10));
        overdue.setExpiredAt(OffsetDateTime.now().minusMinutes(5));

        when(offerRepository.findByDriverIdAndStatus(DRIVER_ID, OfferStatus.EN_ATTENTE))
                .thenReturn(List.of(stillValid, overdue));

        List<ReservationOffer> result = offerService.listPendingOffersForDriver(driver);

        assertThat(result).containsExactly(stillValid);
        assertThat(overdue.getStatus()).isEqualTo(OfferStatus.EXPIREE);
        verify(offerRepository).save(overdue);
    }
}
