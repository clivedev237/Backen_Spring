package com.vora.reservation.domain.model;

import com.vora.reservation.domain.enums.TurnStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Session de transport groupée d'un chauffeur (corridor partagé), distincte
 * de chaque {@link Reservation} (demande individuelle d'un passager).
 * Voir cadrage §5 et §12.1.
 */
@Entity
@Table(name = "turn")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Turn {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Référence logique vers le chauffeur (service Auth). Type BIGINT choisi
     * pour rester cohérent avec users.id (BIGINT UNSIGNED) du dictionnaire de
     * données Auth — voir note de vigilance, cadrage §11.
     */
    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    /** Référence logique vers la trajectoire active du chauffeur (Django Geo). */
    @Column(name = "trajectory_id")
    private UUID trajectoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TurnStatus status = TurnStatus.OUVERT;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity = 4;

    @Column(name = "current_load", nullable = false)
    private Integer currentLoad = 0;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Turn open(Long driverId, UUID trajectoryId, int maxCapacity) {
        Turn turn = new Turn();
        turn.driverId = driverId;
        turn.trajectoryId = trajectoryId;
        turn.maxCapacity = maxCapacity;
        turn.status = TurnStatus.OUVERT;
        turn.currentLoad = 0;
        return turn;
    }

    public boolean hasFreeSeat() {
        return currentLoad < maxCapacity;
    }

    /** Embarquement / insertion d'un passager supplémentaire dans ce Turn. */
    public void incrementLoad() {
        if (!hasFreeSeat()) {
            throw new IllegalStateException("Turn " + id + " est déjà à pleine capacité");
        }
        currentLoad++;
        if (currentLoad >= maxCapacity) {
            status = TurnStatus.COMPLET;
        }
    }

    /**
     * Libère une place (débarquement, ou annulation d'un passager déjà accepté —
     * confirmé, cadrage §5.1) et repasse le Turn en OUVERT si besoin.
     * <p>
     * Ne fait jamais repasser un Turn EN_COURS en OUVERT : "au moins un passager
     * à bord" reste vrai tant que le Turn n'est pas clôturé (cadrage §7.2), même
     * si le compteur de charge redescend temporairement à zéro entre deux
     * embarquements.
     */
    public void releaseSeat() {
        if (currentLoad > 0) {
            currentLoad--;
        }
        if (status == TurnStatus.COMPLET && currentLoad < maxCapacity) {
            status = TurnStatus.OUVERT;
        }
    }

    /**
     * Premier embarquement du Turn (cadrage §7.2 : "au moins un passager est à
     * bord"). Idempotent : un second embarquement dans le même Turn ne
     * réinitialise ni le statut ni started_at.
     */
    public void startBoarding() {
        if (status == TurnStatus.OUVERT || status == TurnStatus.COMPLET) {
            status = TurnStatus.EN_COURS;
        }
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
    }

    /**
     * Clôture définitive du Turn (cadrage §6, étape 14) : appelée uniquement
     * quand tous les passagers sont arrivés (current_load == 0) et qu'aucun
     * nouveau candidat compatible n'a été trouvé lors de la relance de
     * matching.
     */
    public void close() {
        status = TurnStatus.TERMINE;
        endedAt = OffsetDateTime.now();
    }

}
