package com.vora.reservation.infrastructure.persistence;

import com.vora.reservation.domain.model.PaymentReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentReferenceRepository extends JpaRepository<PaymentReference, UUID> {

    Optional<PaymentReference> findByReservationId(UUID reservationId);
}
