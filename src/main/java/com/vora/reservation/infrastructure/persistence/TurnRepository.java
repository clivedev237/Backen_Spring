package com.vora.reservation.infrastructure.persistence;

import com.vora.reservation.domain.enums.TurnStatus;
import com.vora.reservation.domain.model.Turn;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TurnRepository extends JpaRepository<Turn, UUID> {

    List<Turn> findByDriverIdAndStatusIn(Long driverId, List<TurnStatus> statuses);

    /**
     * Verrou pessimiste requis pour l'acceptation atomique d'une offre et la
     * gestion de la concurrence entre chauffeurs (cadrage §8.1).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turn t where t.id = :id")
    Optional<Turn> findByIdForUpdate(@Param("id") UUID id);
}
