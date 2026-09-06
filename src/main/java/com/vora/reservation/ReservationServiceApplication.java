package com.vora.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * VORA — Microservice Réservation (Booking Lifecycle &amp; Matching Orchestration).
 * Phase 1 du plan d'implémentation (cadrage §19) : entités, enums, migrations
 * PostgreSQL et sécurité de base (lecture des headers Gateway).
 */
@SpringBootApplication
public class ReservationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationServiceApplication.class, args);
    }
}
