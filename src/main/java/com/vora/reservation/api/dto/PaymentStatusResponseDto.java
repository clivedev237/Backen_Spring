package com.vora.reservation.api.dto;

import lombok.Value;

import java.time.OffsetDateTime;

/**
 * Réponse DTO pour GET /api/v1/reservations/{id}/payment (statut).
 */
@Value
public class PaymentStatusResponseDto {

    /** Identifiant externe du paiement (payment_links.id). */
    String paymentId;

    /** Jeton du lien de paiement (payment_links.token). */
    String token;

    /** Statut courant (EN_ATTENTE, REUSSI, ECHOUE, EXPIRE). */
    String status;

    /** Montant. */
    String amount;

    /** Moyen de paiement. */
    String paymentMethod;

    /** Dernière mise à jour connue. */
    OffsetDateTime updatedAt;
}
