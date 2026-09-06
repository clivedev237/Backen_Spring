package com.vora.reservation.infrastructure.persistence;

import com.vora.reservation.domain.enums.OfferStatus;
import com.vora.reservation.domain.model.ReservationOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationOfferRepository extends JpaRepository<ReservationOffer, UUID> {

    List<ReservationOffer> findByReservationIdAndStatus(UUID reservationId, OfferStatus status);

    List<ReservationOffer> findByDriverIdAndStatus(Long driverId, OfferStatus status);
}
