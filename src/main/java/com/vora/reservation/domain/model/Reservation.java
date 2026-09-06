package com.vora.reservation.domain.model;

import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Demande de course d'un passager. Garde son propre cycle de statuts et ses
 * propres horodatages d'embarquement/débarquement, indépendamment du Turn
 * auquel elle est rattachée. Voir cadrage §5, §7.1 et §12.2.
 */
@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Référence logique vers le client (service Auth). Type BIGINT — voir Turn.driverId. */
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    /** Renseigné une fois le passager inséré dans un Turn. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id")
    private Turn turn;

    @Column(name = "sequence_index")
    private Integer sequenceIndex;

    @Column(name = "pickup_latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal pickupLongitude;

    @Column(name = "pickup_precision")
    private String pickupPrecision;

    @Column(name = "destination_latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal destinationLatitude;

    @Column(name = "destination_longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal destinationLongitude;

    @Column(name = "destination_address", nullable = false)
    private String destinationAddress;

    @Column(name = "proposed_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal proposedPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReservationStatus status = ReservationStatus.EN_ATTENTE;

    @Column(name = "boarded_at")
    private OffsetDateTime boardedAt;

    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Reservation create(Long clientId, BigDecimal pickupLatitude, BigDecimal pickupLongitude,
                                      String pickupPrecision, BigDecimal destinationLatitude,
                                      BigDecimal destinationLongitude, String destinationAddress,
                                      BigDecimal proposedPrice, PaymentMethod paymentMethod) {
        Reservation reservation = new Reservation();
        reservation.clientId = clientId;
        reservation.pickupLatitude = pickupLatitude;
        reservation.pickupLongitude = pickupLongitude;
        reservation.pickupPrecision = pickupPrecision;
        reservation.destinationLatitude = destinationLatitude;
        reservation.destinationLongitude = destinationLongitude;
        reservation.destinationAddress = destinationAddress;
        reservation.proposedPrice = proposedPrice;
        reservation.paymentMethod = paymentMethod;
        reservation.status = ReservationStatus.EN_ATTENTE;
        return reservation;
    }

    /** Insertion dans un Turn suite à l'acceptation d'une offre. */
    public void assignToTurn(Turn turn, int sequenceIndex) {
        this.turn = turn;
        this.sequenceIndex = sequenceIndex;
        this.status = ReservationStatus.ACCEPTEE;
    }

    public void start() {
        this.status = ReservationStatus.EN_COURS;
        this.boardedAt = OffsetDateTime.now();
    }

    public void confirmArrival() {
        this.status = ReservationStatus.ARRIVEE_CONFIRMEE;
        this.arrivedAt = OffsetDateTime.now();
    }

    /**
     * Annulation. Si le passager avait déjà été accepté par un chauffeur, la
     * place occupée doit être libérée dans le Turn par l'appelant (confirmé,
     * cadrage §5.1) — cette méthode ne fait que changer le statut de la
     * réservation.
     */
    public void cancel() {
        this.status = ReservationStatus.ANNULEE;
    }

    public boolean wasAlreadyAcceptedByDriver() {
        return this.turn != null
                && (status == ReservationStatus.ACCEPTEE || status == ReservationStatus.EN_COURS);
    }

    public void diffuse() {
        if (this.status == ReservationStatus.EN_ATTENTE) {
            this.status = ReservationStatus.DIFFUSEE;
        }
    }
}
