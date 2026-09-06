# VORA — Microservice Réservation — Socle (Phase 1)

Backend Spring Boot 3.x / Java 17. Ce socle correspond à la **Phase 1** du
plan d'implémentation (cadrage v2.1, §19) :

- Entités JPA (`Turn`, `Reservation`, `ReservationOffer`, `PaymentReference`)
- Enums du domaine (`ReservationStatus`, `TurnStatus`, `OfferStatus`, `PaymentMethod`, `PaymentRefStatus`)
- Migrations PostgreSQL (Flyway) dans le schéma `public` de la base **unique et partagée** Render, avec Django (Geo) et Node (Auth & Payment)
- Sécurité de base : lecture des headers d'identité transmis par le Gateway, **aucune validation JWT locale** (mode délégué, confirmé)

> **Correctif Phase 1 (vs cadrage v2.1) :** le cadrage supposait *"base partagée, schéma
> séparé par service"*. Le `VORA_Shared_Database_Guide.md` officiel, qui documente le
> schéma réellement en place sur la base Render, indique au contraire **une seule base,
> un seul schéma (`public`), avec de vraies clés étrangères physiques** (`driver_id` /
> `client_id` → `users.id`). Le code a été corrigé en conséquence : plus de
> `CREATE SCHEMA reservation`, plus de `currentSchema=reservation`, table renommée
> `reservations` (pluriel, comme dans la matrice de propriété du guide), et FK réelles
> ajoutées vers `users`.

## Décisions de cadrage reflétées dans le code

| Décision confirmée | Implémentation |
|---|---|
| JWT validé par le Gateway (mode délégué) | `GatewayHeaderAuthenticationFilter` lit `X-User-Id` / `X-User-Role` / `X-Driver-Id`, pas de `OAuth2ResourceServer` |
| Base unique et partagée, schéma `public` commun (corrigé) | `application.yml` (plus de `currentSchema`) + Flyway sans `CREATE SCHEMA` |
| Durée de validité d'une offre : 5 min | `ReservationOffer.VALIDITY_MINUTES` + `vora.matching.offer-ttl-minutes` |
| Annulation après acceptation chauffeur → place libérée | `Turn.releaseSeat()`, `Reservation.wasAlreadyAcceptedByDriver()` (logique à brancher dans le use case Phase 5/6) |
| Capacité max d'un Turn = 4 | `vora.turn.max-capacity`, contrainte SQL `chk_turn_capacity` |
| Identifiants client/chauffeur en BIGINT avec FK réelle vers `users.id` | `Turn.driverId`, `Reservation.clientId`, `ReservationOffer.driverId` en `Long`, contraintes `REFERENCES users (id)` en base |

## Structure (architecture hexagonale, cadrage §15)

```
com.vora.reservation
├── domain
│   ├── model        # Entités JPA + logique métier (Turn, Reservation, ...)
│   └── enums
├── infrastructure
│   ├── persistence   # Repositories Spring Data JPA
│   └── security      # Filtre Gateway, config Spring Security
└── api               # (à venir, Phase 2) controllers, DTOs, mappers
```

Les packages `application` (use cases), `infrastructure/client-geo`,
`infrastructure/client-payment`, `infrastructure/realtime` et
`infrastructure/notification` seront ajoutés progressivement à partir de la
Phase 2.

## Lancer en local

```bash
cp .env.example .env
# démarrer un Postgres local, ex. :
docker run --name vora-pg -e POSTGRES_DB=vora -e POSTGRES_USER=vora -e POSTGRES_PASSWORD=vora -p 5432:5432 -d postgres:16-alpine

mvn spring-boot:run
```

Flyway crée automatiquement les tables de ce microservice (`turn`, `reservations`,
`reservation_offer`, `payment_reference`) dans le schéma `public` au démarrage. En local,
le Postgres de test ne contient pas la table `users` : soit la créer manuellement au
préalable (voir `VORA_Shared_Database_Guide.md` §5.1), soit pointer `SPRING_DATASOURCE_URL`
vers un dump/instance qui la contient déjà, sous peine d'échec des contraintes FK.

Pour se connecter à la vraie base partagée Render (staging), utiliser
`SPRING_DATASOURCE_PASSWORD` fourni par l'équipe (jamais commité) avec l'URL déjà présente
dans `.env.example`. Comme cette base contient déjà 27 tables créées par Django/Node,
`spring.flyway.baseline-on-migrate=true` est activé pour que Flyway ne tente pas de rejouer
l'historique sur des objets préexistants qu'il ne possède pas.

## Lancer les tests

```bash
mvn test
```

Le test d'intégration (`ReservationServiceApplicationTests`) démarre un vrai
PostgreSQL via Testcontainers, applique les migrations Flyway et vérifie la
persistance des entités de base.

## Build Docker

```bash
docker build -t vora-reservation-service .
docker run -p 8080:8080 --env-file .env vora-reservation-service
```

## Prochaines phases (non couvertes ici)

- **Phase 2** : endpoints de création/consultation de réservation (`api/`)
- **Phase 3-4** : clients REST vers Django Geo (`verify-destination`, `optimize/turn`)
- **Phase 5-6** : diffusion des offres, acceptation atomique, démarrage/arrivée
- **Phase 7** : WebSocket STOMP (`/topic/reservations/{id}/driver-location`)
- **Phase 8** : client REST vers Auth & Payment — **à confirmer avant cette phase** : le
  pattern `payment_links` / SoleasPay découvert dans le dictionnaire de
  données Paiement (voir cadrage §9.2)

## Point de vigilance non encore résolu

~~Le type des identifiants `client_id` / `driver_id` a été fixé en `BIGINT`... reste
à valider avec l'équipe Auth~~ — **résolu** : `VORA_Shared_Database_Guide.md` confirme
`users.id` en `bigint` et la table `users` déjà migrée sur la base partagée ; les FK
physiques `REFERENCES users (id)` ont été ajoutées dans `V1__init_schema.sql`.

## Point de vigilance restant

Les tables propres à ce microservice (`turn`, `reservations`, `reservation_offer`,
`payment_reference`) ne figurent pas encore dans la matrice de propriété (§4) du
`VORA_Shared_Database_Guide.md` partagé par l'équipe Django/infra. À faire remonter
via le circuit de changement de schéma décrit au §10 du guide, pour que le document
soit mis à jour une fois ces tables déployées sur Render.
