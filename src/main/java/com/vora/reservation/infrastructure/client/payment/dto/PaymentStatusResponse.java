package com.vora.reservation.infrastructure.client.payment.dto;

import lombok.Value;

import java.time.OffsetDateTime;

/**
 * Réponse de Node Auth & Payment pour GET /api/v1/payments/{id}/status
 * (cadrage §9.2).
 */
@Value
public class PaymentStatusResponse {

    /** Identifiant externe du paiement (payment_links.id). */
    String id;

    /** Jeton du lien de paiement (payment_links.token). */
    String token;

    /** Statut courant chez Node. */
    String status;

    /** Montant. */
    String amount;

    /** Moyen de paiement. */
    String paymentMethod;

    /** Horodatage de la dernière mise à jour côté Node. */
    OffsetDateTime updatedAt;
}
