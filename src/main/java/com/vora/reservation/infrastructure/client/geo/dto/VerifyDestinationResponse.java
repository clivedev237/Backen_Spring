package com.vora.reservation.infrastructure.client.geo.dto;


import java.util.List;
import java.util.UUID;

/**
 * Corps de la réponse de {@code POST /api/v1/geo/verify-destination}.
 *
 * <p><b>Hypothèse / à confirmer (cadrage §20)</b> : le contrat exact de cet
 * endpoint côté Django Geo n'est pas encore fourni. En attendant, ce DTO
 * documente l'hypothèse retenue et la distinction qu'elle encode :
 * <ul>
 *   <li>{@code valid} répond à la question « la destination est-elle
 *   géocodable et dans la zone de couverture VORA ? ». Si {@code false},
 *   la création de la réservation est rejetée (cadrage §16).</li>
 *   <li>{@code compatibleCorridors} répond à une question différente :
 *   « y a-t-il, MAINTENANT, un chauffeur actif dont le corridor est
 *   compatible ? ». Une liste vide est un résultat parfaitement normal et
 *   NE DOIT PAS bloquer la création : la réservation reste EN_ATTENTE et
 *   sera réévaluée à chaque libération de place chez un chauffeur
 *   compatible (cadrage §5.1). Ce champ n'est pas encore exploité par le
 *   code de la Phase 3 ; il sera consommé à partir de la Phase 4
 *   (optimize/turn) et de la Phase 5 (diffusion des offres).</li>
 * </ul>
 * Si le contrat réel diffère, seul ce fichier (et {@link GeoPoint} /
 * {@link VerifyDestinationRequest}) doit changer — le reste du code
 * applicatif ne dépend que de {@code valid()} et de la taille de
 * {@code compatibleCorridors()}.
 */
public record VerifyDestinationResponse(boolean valid,
                                        Double toleranceMeters,
                                        List<CompatibleCorridor> compatibleCorridors) {
    /** Un chauffeur actif dont le corridor est spatialement compatible. */
    public record CompatibleCorridor(Long driverId, UUID trajectoryId, Double distanceMeters) {
    }
}
