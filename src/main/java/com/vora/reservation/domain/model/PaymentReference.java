package com.vora.reservation.domain.model;

import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.enums.PaymentRefStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reflet local, en lecture seule, du dernier état de paiement connu pour une
 * réservation. La source de vérité reste le service Auth &amp; Payment.
 * Voir cadrage §9.2 (note sur le pattern payment_links / SoleasPay à
 * confirmer) et §12.4.
 */
@Entity
@Table(name = "payment_reference")
@Getter
@Setter
@NoArgsConstructor
public class PaymentReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    /** Identifiant externe renvoyé par Auth & Payment (ex. payment_links.id / token). */
    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentRefStatus status = PaymentRefStatus.EN_ATTENTE;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static PaymentReference initiate(Reservation reservation) {
        PaymentReference ref = new PaymentReference();
        ref.reservation = reservation;
        ref.amount = reservation.getProposedPrice();
        ref.method = reservation.getPaymentMethod();
        ref.status = PaymentRefStatus.EN_ATTENTE;
        return ref;
    }

    public void markSucceeded(String externalPaymentId) {
        this.paymentId = externalPaymentId;
        this.status = PaymentRefStatus.REUSSI;
    }

    public void markFailed() {
        this.status = PaymentRefStatus.ECHOUE;
    }

    /**
     * Mise à jour du statut depuis Node Auth & Payment (source de vérité).
     * À utiliser lors de l'interrogation ou du webhook de confirmation.
     */
    public void syncStatus(PaymentRefStatus status, String externalPaymentId) {
        if (externalPaymentId != null) {
            this.paymentId = externalPaymentId;
        }
        this.status = status;
    }
}
