package com.vora.reservation.api.mapper;
import com.vora.reservation.api.dto.DestinationDto;
import com.vora.reservation.api.dto.PickupLocationDto;
import com.vora.reservation.api.dto.ReservationOfferResponse;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.domain.model.ReservationOffer;
public class ReservationOfferMapper {
    private ReservationOfferMapper() {
    }

    public static ReservationOfferResponse toResponse(ReservationOffer offer) {
        Reservation reservation = offer.getReservation();

        PickupLocationDto pickup = new PickupLocationDto(
                reservation.getPickupLatitude(),
                reservation.getPickupLongitude(),
                reservation.getPickupPrecision()
        );

        DestinationDto destination = new DestinationDto(
                reservation.getDestinationAddress(),
                reservation.getDestinationLatitude(),
                reservation.getDestinationLongitude()
        );

        return new ReservationOfferResponse(
                offer.getId(),
                reservation.getId(),
                offer.getDriverId(),
                offer.getStatus(),
                offer.getSentAt(),
                offer.getAcceptedAt(),
                offer.getExpiredAt(),
                pickup,
                destination,
                reservation.getProposedPrice(),
                reservation.getPaymentMethod()
        );
    }
}
