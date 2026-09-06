package com.vora.reservation.domain.enums;

/**
 * Reflet local (lecture seule) de l'état d'un paiement.
 * La source de vérité reste le service Auth &amp; Payment.
 */
public enum PaymentRefStatus {
    EN_ATTENTE,
    REUSSI,
    ECHOUE,
    EXPIRE
}
