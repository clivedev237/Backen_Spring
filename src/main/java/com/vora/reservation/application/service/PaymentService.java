package com.vora.reservation.application.service;

import com.vora.reservation.application.exception.PaymentAlreadyInitiatedException;
import com.vora.reservation.application.exception.PaymentNotInitiatedException;
import com.vora.reservation.application.exception.PaymentUnavailableException;
import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.enums.PaymentRefStatus;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.model.PaymentReference;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.infrastructure.client.payment.PaymentClient;
import com.vora.reservation.infrastructure.client.payment.dto.InitiatePaymentRequest;
import com.vora.reservation.infrastructure.client.payment.dto.InitiatePaymentResponse;
import com.vora.reservation.infrastructure.client.payment.dto.PaymentStatusResponse;
import com.vora.reservation.infrastructure.persistence.PaymentReferenceRepository;
import com.vora.reservation.infrastructure.persistence.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Use cases de paiement délégués à Node Auth & Payment (cadrage §9.2, §13).
 *
 * <p>Responsabilités :
 * - initier le paiement après confirmation d'arrivée (MTN_MOMO / ORANGE_MONEY)
 * - confirmer les espèces (ESPECES)
 * - interroger le statut d'un paiement déjà initié
 * - refléter localement le statut dans `payment_reference` (lecture/écriture),
 *   dont `Reservation` est enrichi en lecture via un {@code PaymentReference}
 *   joint à la réservation.
 *
 * <p>La source de vérité reste Node. PaymentReference est un cache local
 * synchronisé (pas de webhook implémenté ici, sondage à la place jusqu'à
 * clarification du contrat Node).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentClient paymentClient;
    private final PaymentReferenceRepository paymentReferenceRepository;
    private final ReservationRepository reservationRepository;

    /**
     * Initie le paiement pour une réservation déjà validée (arrivée confirmée).
     *
     * <p>Utilisé par {@link DriveTripService#confirmArrival} (Phase 8) et aussi
     * exposé via {@code POST /api/v1/reservations/{id}/payment} pour les cas où
     * l'initiation doit être déclenchée manuellement ou réessayée.
     *
     * @param reservationId identifiant de la réservation
     * @return réponse d'initiation de Node (id, token, statut initial)
     * @throws PaymentAlreadyInitiatedException si le paiement était déjà initié
     * @throws PaymentNotInitiatedException si la réservation n'est pas dans un
     *         état où le paiement peut être initié (pas d'arrivée confirmée)
     * @throws PaymentUnavailableException si Node est indisponible
     */
    @Transactional
    public InitiatePaymentResponse initiatePayment(UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new com.vora.reservation.application.exception
                        .ReservationNotFoundException(reservationId));

        if (reservation.getStatus() != ReservationStatus.ARRIVEE_CONFIRMEE) {
            throw new PaymentNotInitiatedException(
                    "PAIEMENT_NON_INITIABLE : la réservation n'est pas dans un état permettant d'initier le paiement "
                            + "(statut actuel : " + reservation.getStatus() + ").");
        }

        PaymentReference paymentReference = getOrCreatePaymentReference(reservation);

        if (paymentReference.getStatus() != PaymentRefStatus.EN_ATTENTE
                && paymentReference.getStatus() != PaymentRefStatus.EXPIRE) {
            throw new PaymentAlreadyInitiatedException(
                    "PAIEMENT_DEJA_INITIE : le paiement pour cette réservation a déjà été initié "
                            + "(statut local : " + paymentReference.getStatus() + ").");
        }

        // Délégation à Node (MTN_MOMO / ORANGE_MONEY ; espèces via confirmCash).
        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .amount(reservation.getProposedPrice())
                .currency("XAF")
                .language("fr")
                .channel(null)
                .paymentMethod(reservation.getPaymentMethod())
                .internalReference(paymentReference.getId().toString())
                .paymentLinkToken(null)
                .description(null)
                .build();

        InitiatePaymentResponse nodeResponse;
        try {
            nodeResponse = paymentClient.initiate(request);
        } catch (PaymentUnavailableException e) {
            log.error("Échec initiation paiement réservation {} : {}", reservationId, e.getMessage());
            throw e;
        }

        // Mise à jour locale : le paymentReference devient REUSSI dès que Node
        // déclare le paiement réussi, ou ECHOUE/expiré si Node l'indique.
        // La réponse de Node peut ne pas contenir encore le statut final ;
        // on marque donc EN_ATTENTE et on stocke l'id externe.
        String externalPaymentId = nodeResponse != null && nodeResponse.getId() != null
                ? nodeResponse.getId()
                : null;

        paymentReference.markSucceeded(externalPaymentId);
        paymentReference.setStatus(PaymentRefStatus.EN_ATTENTE);
        paymentReferenceRepository.save(paymentReference);

        // Migration du statut de réservation.
        reservation.startPayment();
        reservationRepository.save(reservation);

        log.info("Paiement initié pour réservation {} (externalPaymentId {}, statut local EN_ATTENTE)",
                reservationId, externalPaymentId);

        return nodeResponse;
    }

    /**
     * Confirme la remise en espèces pour une réservation.
     *
     * <p>Utilisé pour ESPECES (cadrage §9.2, POST /api/v1/payments/cash/confirm).
     * Le paiement en espèces est marqué REUSSI localement une fois Node confirmé.
     *
     * @param reservationId identifiant de la réservation
     * @return réponse de confirmation Node
     * @throws PaymentNotInitiatedException si aucun paiement n'a été initié
     * @throws PaymentUnavailableException si Node est indisponible
     */
    @Transactional
    public PaymentStatusResponse confirmCashPayment(UUID reservationId) {
        PaymentReference paymentReference = paymentReferenceRepository
                .findByReservationId(reservationId)
                .orElseThrow(() -> new PaymentNotInitiatedException(
                        "PAIEMENT_NON_INITIE : aucun paiement n'a été initié pour cette réservation."));

        if (paymentReference.getStatus() == PaymentRefStatus.REUSSI) {
            // Déjà confirmé — idempotence.
            return toStatusResponse(paymentReference);
        }

        String externalPaymentId = paymentReference.getPaymentId();
        if (externalPaymentId == null) {
            throw new PaymentNotInitiatedException(
                    "PAIEMENT_NON_INITIE : le paiement n'a pas encore d'identifiant externe (initiation manquante).");
        }

        PaymentStatusResponse nodeResponse;
        try {
            nodeResponse = paymentClient.confirmCash(externalPaymentId);
        } catch (PaymentUnavailableException e) {
            log.error("Échec confirmation espèces réservation {} : {}", reservationId, e.getMessage());
            throw e;
        }

        // Réflexion locale : espèces confirmées → REUSSI.
        paymentReference.syncStatus(PaymentRefStatus.REUSSI, externalPaymentId);
        paymentReferenceRepository.save(paymentReference);

        // Migration réservation.
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new com.vora.reservation.application.exception
                        .ReservationNotFoundException(reservationId));
        reservation.markPaymentSucceeded();
        reservationRepository.save(reservation);

        log.info("Paiement espèces confirmé pour réservation {} (externalPaymentId {})",
                reservationId, externalPaymentId);

        return nodeResponse;
    }

    /**
     * Interroge le statut d'un paiement déjà initié (sondage Node).
     *
     * <p>À utiliser pour MTN_MOMO / ORANGE_MONEY en attente (cadrage §9.2,
     * GET /api/v1/payments/{id}/status).
     *
     * @param reservationId identifiant de la réservation
     * @return statut connu (local + dernier état Node si disponible)
     * @throws PaymentNotInitiatedException si aucun paiement n'a été initié
     * @throws PaymentUnavailableException si Node est indisponible
     */
    @Transactional(readOnly = true)
    public PaymentStatusResponse queryStatus(UUID reservationId) {
        PaymentReference paymentReference = paymentReferenceRepository
                .findByReservationId(reservationId)
                .orElseThrow(() -> new PaymentNotInitiatedException(
                        "PAIEMENT_NON_INITIE : aucun paiement n'a été initié pour cette réservation."));

        String externalPaymentId = paymentReference.getPaymentId();
        if (externalPaymentId == null) {
            // Pas encore d'id externe → on ne peut pas interroger Node.
            return toStatusResponse(paymentReference);
        }

        try {
            PaymentStatusResponse nodeStatus = paymentClient.getStatus(externalPaymentId);
            // Synchronisation locale depuis Node (source de vérité).
            if (nodeStatus != null && nodeStatus.getStatus() != null) {
                PaymentRefStatus localStatus = mapNodeStatusToLocal(nodeStatus.getStatus());
                paymentReference.syncStatus(localStatus, externalPaymentId);
                paymentReferenceRepository.save(paymentReference);

                // Migration réservation si le statut a changé.
                Reservation reservation = reservationRepository.findById(reservationId)
                        .orElse(null);
                if (reservation != null) {
                    if (localStatus == PaymentRefStatus.REUSSI) {
                        reservation.markPaymentSucceeded();
                    } else if (localStatus == PaymentRefStatus.ECHOUE
                            || localStatus == PaymentRefStatus.EXPIRE) {
                        reservation.markPaymentFailed();
                    }
                    reservationRepository.save(reservation);
                }
            }
            return nodeStatus;
        } catch (PaymentUnavailableException e) {
            log.warn("Échec interrogation statut réservation {} : {}", reservationId, e.getMessage());
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers privés
    // -------------------------------------------------------------------------

    private PaymentReference getOrCreatePaymentReference(Reservation reservation) {
        return paymentReferenceRepository.findByReservationId(reservation.getId())
                .orElseGet(() -> {
                    PaymentReference ref = PaymentReference.initiate(reservation);
                    return paymentReferenceRepository.save(ref);
                });
    }

    private PaymentStatusResponse toStatusResponse(PaymentReference paymentReference) {
        // Construction d'une réponse locale quand Node n'est pas interrogable.
        return new PaymentStatusResponse(
                paymentReference.getPaymentId(),
                null,
                paymentReference.getStatus().name(),
                paymentReference.getAmount().toPlainString(),
                paymentReference.getMethod().name(),
                paymentReference.getUpdatedAt());
    }

    /**
     * Mapping des statuts Node vers PaymentRefStatus local.
     * À ajuster une fois le contrat Node confirmé (EN_ATTENTE / RÉUSSI / ÉCHOUÉ / EXPIRÉ).
     */
    private PaymentRefStatus mapNodeStatusToLocal(String nodeStatus) {
        if (nodeStatus == null) {
            return PaymentRefStatus.EN_ATTENTE;
        }
        return switch (nodeStatus.toUpperCase()) {
            case "EN_ATTENTE", "PENDING" -> PaymentRefStatus.EN_ATTENTE;
            case "RÉUSSI", "REUSSI", "PAID" -> PaymentRefStatus.REUSSI;
            case "ÉCHOUÉ", "ECHOUE", "FAILED" -> PaymentRefStatus.ECHOUE;
            case "EXPIRÉ", "EXPIRE", "EXPIRED" -> PaymentRefStatus.EXPIRE;
            default -> {
                log.warn("Statut Node inconnu {}, traité comme EN_ATTENTE", nodeStatus);
                yield PaymentRefStatus.EN_ATTENTE;
            }
        };
    }
}
