-- VORA - Microservice Reservation
-- V2 : liaison 1--1 entre reservations et payment_reference.
-- Le champ payment_reference_id est rendu obligatoire côté JPA (orphanRemoval = true),
-- mais la colonne reste NULL autorisé en base pour les réservations existantes
-- qui n'ont pas encore de paiement initié (Phase 8).
-- Les réservations sans paiement auront payment_reference_id = NULL ;
-- le service PaymentService.createPaymentReference() créera la ligne et
-- renseignera la FK lors de l'initiation du paiement.

ALTER TABLE reservations
    ADD COLUMN payment_reference_id UUID;

ALTER TABLE reservations
    ADD CONSTRAINT fk_reservations_payment_reference
        FOREIGN KEY (payment_reference_id)
        REFERENCES payment_reference (id)
        ON DELETE SET NULL;

CREATE INDEX idx_reservations_payment_reference_id
    ON reservations (payment_reference_id);
