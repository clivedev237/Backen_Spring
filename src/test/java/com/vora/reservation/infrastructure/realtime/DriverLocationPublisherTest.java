package com.vora.reservation.infrastructure.realtime;

import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.domain.model.Turn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DriverLocationPublisherTest {

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    private DriverLocationPublisher publisher;

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        publisher = new DriverLocationPublisher(messagingTemplate);

        Turn turn = Turn.open(42L, UUID.randomUUID(), 4);
        turn.setId(UUID.randomUUID());

        reservation = Reservation.create(
                7L,
                new BigDecimal("3.866700"),
                new BigDecimal("11.516700"),
                "Devant la pharmacie",
                new BigDecimal("3.883300"),
                new BigDecimal("11.516700"),
                "Bastos, Yaoundé",
                new BigDecimal("1000.00"),
                com.vora.reservation.domain.enums.PaymentMethod.MTN_MOMO);
        reservation.setId(UUID.randomUUID());
        reservation.setStatus(com.vora.reservation.domain.enums.ReservationStatus.EN_COURS);
        reservation.assignToTurn(turn, 0);
    }

    @Test
    void shouldPublishOnCorrectTopicWithLatLngAndOptionalPrecision() {
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DriverLocationPublisher.DriverLocationMessage> messageCaptor =
                ArgumentCaptor.forClass(DriverLocationPublisher.DriverLocationMessage.class);

        publisher.publish(reservation, 3.8701, 11.5203, "Navigo 3m");

        verify(messagingTemplate).convertAndSend(
                destinationCaptor.capture(),
                messageCaptor.capture());

        String expectedTopic = "/topic/reservations/" + reservation.getId() + "/driver-location";
        assertThat(destinationCaptor.getValue())
                .isEqualTo(expectedTopic);

        DriverLocationPublisher.DriverLocationMessage message = messageCaptor.getValue();
        assertThat(message.latitude()).isEqualTo(3.8701);
        assertThat(message.longitude()).isEqualTo(11.5203);
        assertThat(message.precision()).isEqualTo("Navigo 3m");
    }

    @Test
    void shouldPublishWithNullPrecisionWhenNotProvided() {
        ArgumentCaptor<DriverLocationPublisher.DriverLocationMessage> messageCaptor =
                ArgumentCaptor.forClass(DriverLocationPublisher.DriverLocationMessage.class);

        publisher.publish(reservation, 3.8701, 11.5203, null);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/reservations/" + reservation.getId() + "/driver-location"),
                messageCaptor.capture());

        assertThat(messageCaptor.getValue().precision()).isNull();
    }

    @Test
    void shouldPublishWithPrecisionEmptyString() {
        ArgumentCaptor<DriverLocationPublisher.DriverLocationMessage> messageCaptor =
                ArgumentCaptor.forClass(DriverLocationPublisher.DriverLocationMessage.class);

        publisher.publish(reservation, 3.8701, 11.5203, "");

        verify(messagingTemplate).convertAndSend(
                eq("/topic/reservations/" + reservation.getId() + "/driver-location"),
                messageCaptor.capture());

        assertThat(messageCaptor.getValue().precision()).isEmpty();
    }
}
