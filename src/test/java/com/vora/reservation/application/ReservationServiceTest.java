package com.vora.reservation.application;

import com.vora.reservation.api.dto.CreateReservationRequest;
import com.vora.reservation.api.dto.DestinationDto;
import com.vora.reservation.api.dto.PickupLocationDto;
import com.vora.reservation.application.exception.DestinationOutOfCorridorException;
import com.vora.reservation.application.exception.ForbiddenOperationException;
import com.vora.reservation.application.exception.GeoServiceUnavailableException;
import com.vora.reservation.application.exception.ReservationNotFoundException;
import com.vora.reservation.application.service.ReservationService;
import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.infrastructure.client.geo.GeoClient;
import com.vora.reservation.infrastructure.client.geo.dto.VerifyDestinationRequest;
import com.vora.reservation.infrastructure.client.geo.dto.VerifyDestinationResponse;
import com.vora.reservation.infrastructure.persistence.ReservationRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private GeoClient geoClient;

    @InjectMocks
    private ReservationService reservationService;

    private CreateReservationRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateReservationRequest(
                new PickupLocationDto(new BigDecimal("3.866700"), new BigDecimal("11.516700"), "Devant la pharmacie"),
                new DestinationDto("Bastos, Yaoundé", new BigDecimal("3.883300"), new BigDecimal("11.516700")),
                new BigDecimal("1000.00"),
                PaymentMethod.MTN_MOMO
        );
    }

    @Test
    void shouldCreateReservationForClient() {
        AuthenticatedUser client = new AuthenticatedUser(7L, VoraRole.CLIENT, null);
        when(geoClient.verifyDestination(any(VerifyDestinationRequest.class)))
                .thenReturn(new VerifyDestinationResponse(true, 750.0, List.of()));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservation created = reservationService.create(client, request);

        assertThat(created.getClientId()).isEqualTo(7L);
        assertThat(created.getStatus()).isEqualTo(ReservationStatus.EN_ATTENTE);
        verify(geoClient).verifyDestination(any(VerifyDestinationRequest.class));
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void shouldRejectCreationByDriver() {
        AuthenticatedUser driver = new AuthenticatedUser(3L, VoraRole.CHAUFFEUR, 99L);

        assertThatThrownBy(() -> reservationService.create(driver, request))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void shouldRejectCreationWhenDestinationIsOutOfCorridor() {
        AuthenticatedUser client = new AuthenticatedUser(7L, VoraRole.CLIENT, null);
        when(geoClient.verifyDestination(any(VerifyDestinationRequest.class)))
                .thenReturn(new VerifyDestinationResponse(false, null, List.of()));

        assertThatThrownBy(() -> reservationService.create(client, request))
                .isInstanceOf(DestinationOutOfCorridorException.class);
    }

    @Test
    void shouldRejectCreationWhenGeoServiceIsUnavailable() {
        AuthenticatedUser client = new AuthenticatedUser(7L, VoraRole.CLIENT, null);
        when(geoClient.verifyDestination(any(VerifyDestinationRequest.class)))
                .thenThrow(new GeoServiceUnavailableException("Django Geo indisponible", new RuntimeException("timeout")));

        assertThatThrownBy(() -> reservationService.create(client, request))
                .isInstanceOf(GeoServiceUnavailableException.class);
    }

    @Test
    void shouldReturnReservationForOwningClient() {
        Reservation reservation = Reservation.create(7L,
                new BigDecimal("3.866700"), new BigDecimal("11.516700"), null,
                new BigDecimal("3.883300"), new BigDecimal("11.516700"), "Bastos, Yaoundé",
                new BigDecimal("1000.00"), PaymentMethod.MTN_MOMO);
        UUID id = UUID.randomUUID();
        when(reservationRepository.findById(id)).thenReturn(Optional.of(reservation));

        AuthenticatedUser owner = new AuthenticatedUser(7L, VoraRole.CLIENT, null);
        Reservation found = reservationService.getById(owner, id);

        assertThat(found).isSameAs(reservation);
    }

    @Test
    void shouldRejectAccessToAnotherClientsReservation() {
        Reservation reservation = Reservation.create(7L,
                new BigDecimal("3.866700"), new BigDecimal("11.516700"), null,
                new BigDecimal("3.883300"), new BigDecimal("11.516700"), "Bastos, Yaoundé",
                new BigDecimal("1000.00"), PaymentMethod.MTN_MOMO);
        UUID id = UUID.randomUUID();
        when(reservationRepository.findById(id)).thenReturn(Optional.of(reservation));

        AuthenticatedUser otherClient = new AuthenticatedUser(8L, VoraRole.CLIENT, null);

        assertThatThrownBy(() -> reservationService.getById(otherClient, id))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void shouldRejectDriverConsultation() {
        UUID id = UUID.randomUUID();
        AuthenticatedUser driver = new AuthenticatedUser(3L, VoraRole.CHAUFFEUR, 99L);

        assertThatThrownBy(() -> reservationService.getById(driver, id))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void shouldThrowNotFoundForUnknownReservation() {
        UUID id = UUID.randomUUID();
        when(reservationRepository.findById(id)).thenReturn(Optional.empty());
        AuthenticatedUser admin = new AuthenticatedUser(1L, VoraRole.ADMIN, null);

        assertThatThrownBy(() -> reservationService.getById(admin, id))
                .isInstanceOf(ReservationNotFoundException.class);
    }
}
