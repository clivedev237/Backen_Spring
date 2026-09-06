-- Stub de test (base jetable Testcontainers uniquement) : la table "users"
-- est la propriété des services Django/Node sur la base partagée Render ;
-- elle n'est jamais créée par les migrations Flyway de ce microservice
-- (voir V1__init_schema.sql, FK réelles REFERENCES users (id)). Sans elle,
-- V1 ne peut pas s'appliquer sur une base vide.
-- Le seed d'ids suffit aux INSERT des tests (FK client_id / driver_id).

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY
);

INSERT INTO users (id)
SELECT g FROM generate_series(1, 1000) AS g
ON CONFLICT (id) DO NOTHING;
