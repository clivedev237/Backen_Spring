package com.vora.reservation.domain.model;

import com.vora.reservation.domain.enums.OfferStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Offre diffusée à un chauffeur candidat pour une {@link Reservation}.
 * Durée de validité confirmée : 5 minutes à partir de la diffusion (sentAt).
 * Voir cadrage §8.1 et §12.3.
 */
@Entity
@Table(name = "reservation_offer")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OfferStatus status = OfferStatus.EN_ATTENTE;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    public static final int VALIDITY_MINUTES = 5;

    public static ReservationOffer sendTo(Reservation reservation, Long driverId) {
        ReservationOffer offer = new ReservationOffer();
        offer.reservation = reservation;
        offer.driverId = driverId;
        offer.status = OfferStatus.EN_ATTENTE;
        offer.sentAt = OffsetDateTime.now();
        offer.expiredAt = offer.sentAt.plusMinutes(VALIDITY_MINUTES);
        return offer;
    }

    public boolean isExpired(OffsetDateTime now) {
        return status == OfferStatus.EN_ATTENTE && expiredAt != null && now.isAfter(expiredAt);
    }

    public void accept() {
        this.status = OfferStatus.ACCEPTEE;
        this.acceptedAt = OffsetDateTime.now();
    }

    public void invalidate() {
        if (this.status == OfferStatus.EN_ATTENTE) {
            this.status = OfferStatus.INVALIDEE;
        }
    }
    public void decline() {
        this.status = OfferStatus.REFUSEE;
    }
}
