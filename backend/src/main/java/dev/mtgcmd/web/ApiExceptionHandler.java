package dev.mtgcmd.web;

import dev.mtgcmd.catalog.CardServiceException;
import dev.mtgcmd.store.RoomStore;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Error translation limited to the application API controllers. */
@RestControllerAdvice(
    assignableTypes = {SessionController.class, RoomController.class, DeckController.class})
public final class ApiExceptionHandler {
  private static final String MESSAGE_FIELD = "message";
  static final String LOGGER_NAME = "dev.mtgcmd.web.ApiController";
  private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

  @ExceptionHandler(RoomStore.Conflict.class)
  public ResponseEntity<?> conflict(RoomStore.Conflict failure) {
    return ResponseEntity.status(409)
        .body(Map.of(MESSAGE_FIELD, "Table changed; refresh and retry"));
  }

  @ExceptionHandler(RoomStore.Forbidden.class)
  public ResponseEntity<?> forbidden(final RoomStore.Forbidden failure) {
    return ResponseEntity.status(403).body(Map.of(MESSAGE_FIELD, failure.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> invalid(final IllegalArgumentException failure) {
    if (failure instanceof CardServiceException) {
      LOG.warn("Card service operation failed", failure);
    }
    return ResponseEntity.badRequest().body(Map.of(MESSAGE_FIELD, failure.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> failed(final Exception failure) {
    LOG.error("HTTP request failed", failure);
    return ResponseEntity.status(500)
        .body(
            Map.of(
                MESSAGE_FIELD,
                "Request failed; saved state was not acknowledged. Refresh before retrying."));
  }
}
