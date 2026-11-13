package se.segersten.wreckage.game.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;

import se.segersten.wreckage.game.application.GameNotFoundException;

@RestControllerAdvice
public class GameExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameExceptionHandler.class);

    @ExceptionHandler(GameNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleGameNotFound(GameNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidArgument(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException exception,
                                                     HttpServletRequest request) {
        LOGGER.error("Failed to persist game data for {} {}", request.getMethod(), request.getRequestURI(), exception);
        return new ErrorResponse("Could not save game data because of an internal server error.");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInvalidState(IllegalStateException exception) { return new ErrorResponse(exception.getMessage()); }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(SecurityException exception) { return new ErrorResponse(exception.getMessage()); }

    public record ErrorResponse(String message) {
    }
}
