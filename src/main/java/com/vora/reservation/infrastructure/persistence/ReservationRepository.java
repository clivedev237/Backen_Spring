package com.vora.reservation.infrastructure.persistence;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.model.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {


    List<Reservation> findByClientId(Long clientId);

    /** Réservations en attente à réévaluer à chaque libération de place (cadrage §5.1). */
    List<Reservation> findByStatus(ReservationStatus status);

    /** Consultation paginée des réservations d'un client (Phase 2, cadrage §19). */
    Page<Reservation> findByClientId(Long clientId, Pageable pageable);

    Page<Reservation> findByClientIdAndStatus(Long clientId, ReservationStatus status, Pageable pageable);

    Page<Reservation> findByStatus(ReservationStatus status, Pageable pageable);

    /**
     * Verrou pessimiste requis pour l'acceptation atomique d'une offre
     * (premier chauffeur gagnant, cadrage §8.1).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);
}
