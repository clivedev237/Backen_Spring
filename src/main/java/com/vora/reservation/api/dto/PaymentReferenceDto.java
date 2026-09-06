package com.vora.reservation.api.dto;

import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.enums.PaymentRefStatus;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projection en lecture de la référence de paiement locale.
 * Source de vérité : Node Auth & Payment.
 */
@Value
public class PaymentReferenceDto {

    UUID id;

    /** Identifiant externe (payment_links.id). */
    String paymentId;

    /** Montant. */
    BigDecimal amount;

    /** Moyen de paiement. */
    PaymentMethod method;

    /** Statut local. */
    PaymentRefStatus status;

    /** Dernière mise à jour locale. */
    OffsetDateTime updatedAt;
}
