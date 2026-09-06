package com.vora.reservation.infrastructure.client.geo.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Corps de la requête {@code POST /api/v1/optimize/turn} (cadrage §9.1, §5).
 *
 * <p><b>Hypothèse / à confirmer (cadrage §20)</b> : comme pour
 * {@link VerifyDestinationRequest} en Phase 3, le contrat exact de cet
 * endpoint n'a pas été fourni par l'équipe Django au moment de cette
 * livraison. L'hypothèse retenue : Réservation transmet le point de
 * ramassage/destination, le prix proposé, et la liste des corridors
 * compatibles déjà renvoyés par {@code verify-destination}
 * ({@code VerifyDestinationResponse#compatibleCorridors()}) — c'est Django
 * Geo qui calcule le score (proposed_price − detour_cost + urgency_bonus,
 * cadrage §5) et la position d'insertion gloutonne pour chacun de ces
 * candidats ; Réservation ne réimplémente jamais cet algorithme.
// * Si le contrat réel diffère, seul ce fichier (et {@link })
 * est à revoir.
 */
public record OptimizeTurnRequest(Long driverId, Integer zoneId) {

    public OptimizeTurnRequest(Long driverId) {
        this(driverId, null);
    }
}
