-- VORA - Microservice Reservation
-- V1 : schéma initial, conforme à VORA_Shared_Database_Guide.md
-- CORRECTIF Phase 1 : la base est UNIQUE et PARTAGÉE avec Django et Node,
-- toutes les tables vivent dans le schéma "public" (pas de schéma "reservation"
-- dédié, contrairement à ce que supposait le cadrage v2.1 §3/§20). Les
-- identifiants client/chauffeur référencent directement users.id par une
-- vraie contrainte de clé étrangère (règle guide §3.1).

-- Nécessaire pour gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================================
-- Table turn : session de transport groupée d'un chauffeur (corridor partagé)
-- =========================================================================
CREATE TABLE turn (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Chauffeur porté par le service Auth (Node). FK réelle vers users.id
    -- (BIGINT, table déjà existante sur la base partagée).
    driver_id       BIGINT NOT NULL REFERENCES users (id),

    -- Référence logique vers la trajectoire active du chauffeur (Django Geo,
    -- table driver_trajectories). Pas de FK physique : table hors du
    -- périmètre de ce microservice, gérée par Django (guide §2, règle 2).
    trajectory_id   UUID NULL,

    status          VARCHAR(20)  NOT NULL DEFAULT 'OUVERT',
    max_capacity    INTEGER      NOT NULL DEFAULT 4,
    current_load    INTEGER      NOT NULL DEFAULT 0,

    started_at      TIMESTAMPTZ  NULL,
    ended_at        TIMESTAMPTZ  NULL,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_turn_status CHECK (status IN ('OUVERT', 'COMPLET', 'EN_COURS', 'TERMINE', 'ANNULE')),
    CONSTRAINT chk_turn_capacity CHECK (current_load >= 0 AND current_load <= max_capacity)
);

CREATE INDEX idx_turn_driver_id ON turn (driver_id);
CREATE INDEX idx_turn_status ON turn (status);

-- =========================================================================
-- Table reservations : demande d'un passager (cycle de vie propre)
-- Nom au pluriel pour rester cohérent avec la matrice de propriété des
-- tables du guide partagé (§4 et §8 : "reservations" — table Spring Boot).
-- =========================================================================
CREATE TABLE reservations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Passager, porté par le service Auth (Node). FK réelle vers users.id.
    client_id               BIGINT NOT NULL REFERENCES users (id),

    turn_id                 UUID NULL REFERENCES turn (id),
    sequence_index          INTEGER NULL,

    pickup_latitude         DECIMAL(9,6) NOT NULL,
    pickup_longitude        DECIMAL(9,6) NOT NULL,
    pickup_precision        VARCHAR(255) NULL,

    destination_latitude    DECIMAL(9,6) NOT NULL,
    destination_longitude   DECIMAL(9,6) NOT NULL,
    destination_address     VARCHAR(255) NOT NULL,

    proposed_price           DECIMAL(10,2) NOT NULL,
    payment_method           VARCHAR(20)   NOT NULL,
    status                   VARCHAR(30)   NOT NULL DEFAULT 'EN_ATTENTE',

    boarded_at              TIMESTAMPTZ NULL,
    arrived_at              TIMESTAMPTZ NULL,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_reservation_price CHECK (proposed_price > 0),
    CONSTRAINT chk_reservation_payment_method CHECK (payment_method IN ('ORANGE_MONEY', 'MTN_MOMO', 'ESPECES')),
    CONSTRAINT chk_reservation_status CHECK (status IN (
        'EN_ATTENTE', 'DIFFUSEE', 'ACCEPTEE', 'EN_COURS', 'ARRIVEE_CONFIRMEE',
        'PAIEMENT_EN_COURS', 'TERMINEE', 'ANNULEE', 'EXPIREE', 'PAIEMENT_ECHOUE'
    ))
);

CREATE INDEX idx_reservation_client_id ON reservations (client_id);
CREATE INDEX idx_reservation_turn_id ON reservations (turn_id);
CREATE INDEX idx_reservation_status ON reservations (status);

-- =========================================================================
-- Table reservation_offer : offre diffusée à un chauffeur pour une réservation
-- =========================================================================
CREATE TABLE reservation_offer (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id  UUID NOT NULL REFERENCES reservations (id),
    driver_id       BIGINT NOT NULL REFERENCES users (id),

    status          VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',

    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at     TIMESTAMPTZ NULL,
    -- Durée de validité d'une offre confirmée à 5 minutes (voir vora.matching.offer-ttl-minutes)
    expired_at      TIMESTAMPTZ NULL,

    CONSTRAINT chk_offer_status CHECK (status IN ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE', 'EXPIREE', 'INVALIDEE'))
);

CREATE INDEX idx_offer_reservation_id ON reservation_offer (reservation_id);
CREATE INDEX idx_offer_driver_id ON reservation_offer (driver_id);
CREATE INDEX idx_offer_status ON reservation_offer (status);

-- =========================================================================
-- Table payment_reference : reflet local en lecture seule de l'état du
-- paiement ; la source de vérité reste le service Auth & Payment
-- (table payment_links, propriété de Node — guide §5.3).
-- =========================================================================
CREATE TABLE payment_reference (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id  UUID NOT NULL UNIQUE REFERENCES reservations (id),

    -- Identifiant externe renvoyé par Auth & Payment (ex. payment_links.id / token).
    payment_id      VARCHAR(100) NULL,

    amount          DECIMAL(10,2) NOT NULL,
    method          VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',

    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_payment_ref_status CHECK (status IN ('EN_ATTENTE', 'REUSSI', 'ECHOUE', 'EXPIRE'))
);

CREATE INDEX idx_payment_reference_status ON payment_reference (status);
