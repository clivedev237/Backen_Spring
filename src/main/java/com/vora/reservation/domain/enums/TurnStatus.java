package com.vora.reservation.domain.enums;

/**
 * Statuts d'un {@code Turn} (corridor partagé d'un chauffeur). Voir cadrage §7.2.
 */
public enum TurnStatus {
    /** Actif, peut encore accepter des passagers (capacité non atteinte). */
    OUVERT,
    /** Capacité maximale atteinte (4 passagers) ; repasse en OUVERT dès qu'une place se libère. */
    COMPLET,
    /** Au moins un passager est à bord. */
    EN_COURS,
    /** Tous les passagers sont arrivés et payés. */
    TERMINE,
    /** Turn interrompu (ex. chauffeur devenu indisponible). */
    ANNULE
}
