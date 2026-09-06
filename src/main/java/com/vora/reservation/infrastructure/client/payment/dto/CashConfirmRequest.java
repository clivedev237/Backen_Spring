package com.vora.reservation.infrastructure.client.payment.dto;

import lombok.Value;

/**
 * Payload pour POST /api/v1/payments/cash/confirm (cadrage §9.2).
 * Nécessaire car le confirmCash() de PaymentClient envoie un body.
 */
@Value
public class CashConfirmRequest {

    /** Identifiant externe du paiement en espèces (payment_links.id). */
    String paymentId;
}
