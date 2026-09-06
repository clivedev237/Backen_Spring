package com.vora.reservation.infrastructure.client.payment.dto;

import lombok.Value;

import java.time.OffsetDateTime;

/**
 * Réponse de Node Auth & Payment après initiation d'un paiement (cadrage §9.2).
 *
 * <p>Correspond au dictionnaire payment_links (table propriété de Node) :
 * - id           → payment_links.id
 * - token        → payment_links.token
 * - status       → payment_links.status
 * - created_at   → payment_links.created_at
 */
@Value
public class InitiatePaymentResponse {

    /** Identifiant externe du paiement chez Node (payment_links.id). */
    String id;

    /** Jeton utilisé pour accéder au lien de paiement (payment_links.token). */
    String token;

    /** Statut initial renvoyé par Node. */
    String status;

    /** Horodatage de création côté Node. */
    OffsetDateTime createdAt;

    /** Montant confirmé par Node. */
    String amount;

    /** Moyen de paiement. */
    String paymentMethod;
}
