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
  static final String LOGGER_NAME = "dev.mtgcmd.web.ApiController";
  private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

  @ExceptionHandler(RoomStore.Conflict.class)
  public ResponseEntity<?> conflict(RoomStore.Conflict e) {
    return ResponseEntity.status(409).body(Map.of("message", "Table changed; refresh and retry"));
  }

  @ExceptionHandler(RoomStore.Forbidden.class)
  public ResponseEntity<?> forbidden(RoomStore.Forbidden e) {
    return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> invalid(IllegalArgumentException e) {
    if (e instanceof CardServiceException) LOG.warn("Card service operation failed", e);
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> failed(Exception e) {
    LOG.error("HTTP request failed", e);
    return ResponseEntity.status(500)
        .body(
            Map.of(
                "message",
                "Request failed; saved state was not acknowledged. Refresh before retrying."));
  }
}
