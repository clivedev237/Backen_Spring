package com.vora.reservation.api;

import com.vora.reservation.api.dto.ErrorResponse;
import com.vora.reservation.application.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ReservationNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenOperationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "Forbidden", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.ofValidation(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "La requête contient des champs invalides.", request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(DestinationOutOfCorridorException.class)
    public ResponseEntity<ErrorResponse> handleDestinationOutOfCorridor(DestinationOutOfCorridorException ex,
                                                                        HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), "Unprocessable Entity", ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(GeoServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleGeoServiceUnavailable(GeoServiceUnavailableException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE.value(), "Service Unavailable", ex.getMessage(),
                        request.getRequestURI()));
    }
    @ExceptionHandler(ReservationOfferNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOfferNotFound(ReservationOfferNotFoundException ex,
                                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    /** COURSE_DEJA_ATTRIBUEE (cadrage §8.1) : un autre chauffeur a déjà gagné. */
    @ExceptionHandler(OfferAlreadyResolvedException.class)
    public ResponseEntity<ErrorResponse> handleOfferAlreadyResolved(OfferAlreadyResolvedException ex,
                                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    /** OFFRE_EXPIREE : délai de 5 minutes dépassé (vérification paresseuse, Phase 5). */
    @ExceptionHandler(OfferExpiredException.class)
    public ResponseEntity<ErrorResponse> handleOfferExpired(OfferExpiredException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(
                ErrorResponse.of(HttpStatus.GONE.value(), "Gone", ex.getMessage(), request.getRequestURI()));
    }

    /** TURN_COMPLET : la dernière place a été prise entre-temps par une autre acceptation. */
    @ExceptionHandler(TurnFullException.class)
    public ResponseEntity<ErrorResponse> handleTurnFull(TurnFullException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    /** OFFRE_INTROUVABLE : aucune offre acceptée pour la réservation (Phase 6). */
    @ExceptionHandler(OfferNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOfferNotFound(OfferNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    /** OFFRE_NON_ADRESSEE : l'offre n'a pas été diffusée à ce chauffeur (Phase 6). */
    @ExceptionHandler(OfferNotAddressedToThisDriverException.class)
    public ResponseEntity<ErrorResponse> handleOfferNotAddressed(OfferNotAddressedToThisDriverException ex,
                                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "Forbidden", ex.getMessage(), request.getRequestURI()));
    }

    /** COURSE_NON_ATTRIBUEE : la réservation n'est pas affectée à un chauffeur (Phase 6). */
    @ExceptionHandler(ReservationNotAcceptedException.class)
    public ResponseEntity<ErrorResponse> handleReservationNotAccepted(ReservationNotAcceptedException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    /** COURSE_NON_DEMARREE : la réservation n'est pas dans un état démarrable (Phase 6). */
    @ExceptionHandler(ReservationNotStartedException.class)
    public ResponseEntity<ErrorResponse> handleReservationNotStarted(ReservationNotStartedException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    /** TURN_INTROUVABLE : le Turn associé à une réservation acceptée est introuvable (Phase 6). */
    @ExceptionHandler(TurnNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTurnNotFound(TurnNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI()));
    }
}
