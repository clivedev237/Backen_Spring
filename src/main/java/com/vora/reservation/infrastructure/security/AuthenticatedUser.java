package com.vora.reservation.infrastructure.security;

/**
 * Identité de l'appelant, reconstituée à partir des headers transmis par le
 * Gateway (mode de validation JWT délégué, confirmé — cadrage §2, §3, §14).
 * Le microservice Réservation ne valide aucun JWT localement : il fait
 * confiance à l'identité posée par le Gateway en amont.
 */
public record AuthenticatedUser(
        Long userId,
        VoraRole role,
        Long driverId // renseigné uniquement si role == CHAUFFEUR
) {
    public boolean isClient() {
        return role == VoraRole.CLIENT;
    }

    public boolean isDriver() {
        return role == VoraRole.CHAUFFEUR;
    }
}
