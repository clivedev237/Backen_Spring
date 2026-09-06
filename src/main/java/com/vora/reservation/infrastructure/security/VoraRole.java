package com.vora.reservation.infrastructure.security;

/**
 * Rôles métier transmis par le Gateway via le header {@code X-User-Role}.
 * À aligner avec le contenu de la table {@code roles} du service Auth
 * (permissions JSON) une fois son référentiel exact confirmé.
 */
public enum VoraRole {
    CLIENT,
    CHAUFFEUR,
    ADMIN
}
