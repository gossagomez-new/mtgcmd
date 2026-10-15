package dev.mtgcmd.web;

import jakarta.servlet.http.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Guest-session HTTP bootstrap. */
@RestController
@RequestMapping("/api")
public final class SessionController {
  private final GuestSessions guests;

  public SessionController(final GuestSessions guests) {
    this.guests = guests;
  }

  @GetMapping("/session")
  public Map<String, String> session(
      final HttpServletRequest request, final HttpServletResponse response) {
    return Map.of("csrf", guests.bootstrap(request, response).csrf());
  }
}
