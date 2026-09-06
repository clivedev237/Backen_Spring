package com.vora.reservation.api.dto;

import lombok.Value;

import java.time.OffsetDateTime;

/**
 * Réponse DTO pour POST /api/v1/reservations/{id}/payment (initiation).
 *
 * <p>Reflet client-side des champs renvoyés par Node Auth & Payment après
 * initiation (voir dictionnaire payment_links : id, token, status).
 */
@Value
public class InitiatePaymentResponseDto {

    /** Identifiant externe du paiement (payment_links.id). */
    String paymentId;

    /** Jeton du lien de paiement (payment_links.token). */
    String token;

    /** Statut initial renvoyé par Node. */
    String status;

    /** Horodatage de création côté Node. */
    OffsetDateTime createdAt;

    /** Montant. */
    String amount;

    /** Moyen de paiement. */
    String paymentMethod;
}
