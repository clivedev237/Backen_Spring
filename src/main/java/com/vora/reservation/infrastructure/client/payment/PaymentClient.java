package com.vora.reservation.infrastructure.client.payment;

import com.vora.reservation.application.exception.PaymentUnavailableException;
import com.vora.reservation.infrastructure.client.payment.dto.CashConfirmRequest;
import com.vora.reservation.infrastructure.client.payment.dto.InitiatePaymentRequest;
import com.vora.reservation.infrastructure.client.payment.dto.InitiatePaymentResponse;
import com.vora.reservation.infrastructure.client.payment.dto.PaymentStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client REST synchrone vers Node Auth & Payment (cadrage §9.2, §13).
 *
 * <p>Appelé par Réservation après validation d'arrivée pour initier le
 * règlement (ORANGE_MONEY / MTN_MOMO) ou confirmer les espèces.
 * Le paiement en espèces est initié côté backend réservation (pas d'appel
 * Node pour la demander, uniquement pour confirmer la remise).
 *
 * <p>Source de vérité du statut : Node Auth & Payment. Cette classe ne fait
 * que reflecter localement le dernier état connu via `payment_reference`.
 */
@Component
@Slf4j
public class PaymentClient {

    private final RestClient paymentRestClient;

    public PaymentClient(@Qualifier("paymentRestClient") RestClient paymentRestClient) {
        this.paymentRestClient = paymentRestClient;
    }

    /**
     * Initie un paiement chez Node Auth & Payment (cadrage §9.2).
     *
     * @param request détails du paiement (montant, devise, moyen, etc.)
     * @return réponse de Node (id, token/link, statut initial)
     * @throws PaymentUnavailableException si Node est indisponible ou lent
     */
    public InitiatePaymentResponse initiate(InitiatePaymentRequest request) {
        try {
            return paymentRestClient.post()
                    .uri("/api/v1/payments/initiate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(InitiatePaymentResponse.class);
        } catch (RestClientException ex) {
            log.warn("Appel à Node Auth & Payment (initiate) en échec : {}", ex.getMessage());
            throw new PaymentUnavailableException(
                    "Service de paiement indisponible ou trop lent.", ex);
        }
    }

    /**
     * Interrogation du statut d'un paiement déjà initié (cadrage §9.2,
     * GET /api/v1/payments/{id}/status).
     *
     * @param externalPaymentId identifiant renvoyé par Node lors de l'initiation
     * @return dernier état connu (en attente, réussi, échoué, expiré)
     * @throws PaymentUnavailableException si Node est indisponible ou lent
     */
    public PaymentStatusResponse getStatus(String externalPaymentId) {
        try {
            return paymentRestClient.get()
                    .uri("/api/v1/payments/{id}/status", externalPaymentId)
                    .retrieve()
                    .body(PaymentStatusResponse.class);
        } catch (RestClientException ex) {
            log.warn("Appel à Node Auth & Payment (status) en échec : {}", ex.getMessage());
            throw new PaymentUnavailableException(
                    "Service de paiement indisponible ou trop lent (statut).", ex);
        }
    }

    /**
     * Confirmation de remise en espèces (cadrage §9.2,
     * POST /api/v1/payments/cash/confirm).
     *
     * @param externalPaymentId identification du paiement en espèces
     * @return réponse de Node confirmant la clôture
     * @throws PaymentUnavailableException si Node est indisponible ou lent
     */
    public PaymentStatusResponse confirmCash(String externalPaymentId) {
        try {
            return paymentRestClient.post()
                    .uri("/api/v1/payments/cash/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CashConfirmRequest(externalPaymentId))
                    .retrieve()
                    .body(PaymentStatusResponse.class);
        } catch (RestClientException ex) {
            log.warn("Appel à Node Auth & Payment (cash/confirm) en échec : {}", ex.getMessage());
            throw new PaymentUnavailableException(
                    "Service de paiement indisponible ou trop lent (cash/confirm).", ex);
        }
    }
}
