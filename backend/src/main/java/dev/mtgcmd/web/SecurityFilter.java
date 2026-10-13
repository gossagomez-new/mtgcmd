package dev.mtgcmd.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

public final class SecurityFilter extends OncePerRequestFilter {
  private final GuestSessions guests;
  private final String origin;

  public SecurityFilter(GuestSessions guests, String origin) {
    this.guests = guests;
    this.origin = origin;
  }

  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "same-origin");
    response.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; img-src 'self' https://*.scryfall.io data:; connect-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
    String path = request.getRequestURI();
    if (path.startsWith("/api/") || path.equals("/ws")) {
      response.setHeader("Cache-Control", "no-store");
      try {
        if (!path.equals("/api/session")) {
          GuestSessions.Guest guest = guests.require(request);
          if (!request.getMethod().equals("GET") || path.equals("/ws")) {
            if (!origin.equals(request.getHeader("Origin"))) {
              forbidden(response);
              return;
            }
            if (!path.equals("/ws") && !equal(guest.csrf(), request.getHeader("X-CSRF-Token"))) {
              forbidden(response);
              return;
            }
          }
          if (!request.getMethod().equals("GET")
              && (request.getContentLengthLong() < 0 || request.getContentLengthLong() > 100000)) {
            response.setStatus(413);
            response.setContentType("application/json");
            response
                .getWriter()
                .write("{\"message\":\"Request too large or missing content length\"}");
            return;
          }
        }
      } catch (dev.mtgcmd.store.RoomStore.Forbidden e) {
        forbidden(response);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private static boolean equal(String expected, String actual) {
    return actual != null
        && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }

  private static void forbidden(HttpServletResponse response) throws IOException {
    response.setStatus(403);
    response.setContentType("application/json");
    response
        .getWriter()
        .write("{\"message\":\"Guest session, origin, or CSRF validation failed\"}");
  }
}
