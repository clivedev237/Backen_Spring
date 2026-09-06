package com.vora.reservation.infrastructure.client.payment.dto;

import com.vora.reservation.domain.enums.PaymentMethod;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Payload envoyé à Node Auth & Payment pour initier un paiement (cadrage §9.2,
 * POST /api/v1/payments/initiate — orange-money / mtn-momo / cash/confirm).
 *
 * <p>Les noms de champs sont en snake_case car ils seront sérialisés par le
 * PaymentClientConfig dédié (PropertyNamingStrategies.SNAKE_CASE).
 */
@Value
@Builder
public class InitiatePaymentRequest {

    /**
     * Référence logique vers le client (service Auth & Payment, table users).
     * INDISPENSABLE : sans cet identifiant, Node ne peut pas savoir qui
     * facturer, ni retrouver le numéro de téléphone à débiter (BUG corrigé :
     * ce champ était absent du payload alors que PaymentClient l'envoie déjà
     * à Node).
     */
    Long clientId;

    /** Montant à payer (CFA), recopié depuis proposed_price de la réservation. */
    BigDecimal amount;

    /** Devise — dans le dictionnaire payment_links la valeur par défaut est XAF. */
    String currency;

    /** Langue du lien de paiement — défaut fr selon le dictionnaire. */
    String language;

    /** Canal d'envoi du lien de paiement (mail / sms) — si applicable. */
    String channel;

    /** Moyen de paiement sélectionné : ORANGE_MONEY, MTN_MOMO, ESPECES. */
    PaymentMethod paymentMethod;

    /** Référence interne VORA (payment_reference.id) pour la corrélation. */
    String internalReference;

    /** Jeton / identifiant du lien de paiement si Node le retourne dans l'URL. */
    String paymentLinkToken;

    /** Motif ou description du paiement. */
    String description;

    /**
     * Constructeur adjoint : depuis une réservation + identifiant de lien.
     */
    public static InitiatePaymentRequest from(
            com.vora.reservation.domain.model.Reservation reservation,
            String paymentReferenceId,
            String paymentLinkToken) {
        return InitiatePaymentRequest.builder()
                .clientId(reservation.getClientId())
                .amount(reservation.getProposedPrice())
                .currency("XAF")
                .language("fr")
                .channel(null)
                .paymentMethod(reservation.getPaymentMethod())
                .internalReference(paymentReferenceId)
                .paymentLinkToken(paymentLinkToken)
                .description("Course VORA - réservation " + reservation.getId())
                .build();
    }
}
