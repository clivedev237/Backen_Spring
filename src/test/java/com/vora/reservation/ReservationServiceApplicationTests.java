package com.vora.reservation;

import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.domain.model.Turn;
import com.vora.reservation.infrastructure.persistence.ReservationRepository;
import com.vora.reservation.infrastructure.persistence.TurnRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que le contexte Spring démarre, que les migrations Flyway
 * s'appliquent sur une vraie instance PostgreSQL (Testcontainers), et que
 * les entités de base (Turn, Reservation) se persistent correctement.
 */
@Testcontainers
@SpringBootTest
class ReservationServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vora")
            .withUsername("vora")
            .withPassword("vora")
            // La table partagée "users" (propriété Django/Node) n'existe pas sur une base jetable ;
            // on y pose un stub minimal avant les migrations Flyway (voir db/users-stub.sql).
            .withInitScript("db/users-stub.sql");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TurnRepository turnRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldPersistTurnAndReservation() {
        Turn turn = Turn.open(42L, null, 4);
        turnRepository.save(turn);

        Reservation reservation = Reservation.create(
                7L,
                new BigDecimal("3.866700"), new BigDecimal("11.516700"), "Devant la pharmacie",
                new BigDecimal("3.883300"), new BigDecimal("11.516700"), "Bastos, Yaoundé",
                new BigDecimal("1000.00"), PaymentMethod.MTN_MOMO
        );
        reservationRepository.save(reservation);

        assertThat(turnRepository.findById(turn.getId())).isPresent();
        assertThat(reservationRepository.findById(reservation.getId())).isPresent();
    }
}
