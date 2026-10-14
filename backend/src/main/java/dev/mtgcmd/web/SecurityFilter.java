package dev.mtgcmd.web;

import dev.mtgcmd.store.RoomStore;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

public final class SecurityFilter extends OncePerRequestFilter {
  private static final long MAX_REQUEST_BYTES = 100_000L;
  private final GuestSessions guests;
  private final String origin;

  public SecurityFilter(GuestSessions guests, String origin) {
    this.guests = guests;
    this.origin = origin;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    writeSecurityHeaders(response);
    if (isProtectedEndpoint(request)) {
      response.setHeader("Cache-Control", "no-store");
      if (!authorize(request, response)) {
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private static void writeSecurityHeaders(HttpServletResponse response) {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "same-origin");
    response.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; img-src 'self' https://*.scryfall.io data:; connect-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
  }

  private boolean authorize(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    if ("/api/session".equals(request.getRequestURI())) {
      return true;
    }
    try {
      GuestSessions.Guest guest = guests.require(request);
      if (requiresOrigin(request) && !origin.equals(request.getHeader("Origin"))) {
        forbidden(response);
        return false;
      }
      if (requiresCsrf(request) && !equal(guest.csrf(), request.getHeader("X-CSRF-Token"))) {
        forbidden(response);
        return false;
      }
      if (requiresBodyLimit(request) && !hasAllowedContentLength(request)) {
        requestTooLarge(response);
        return false;
      }
      return true;
    } catch (RoomStore.Forbidden exception) {
      forbidden(response);
      return false;
    }
  }

  private static boolean isWebSocket(HttpServletRequest request) {
    return "/ws".equals(request.getRequestURI());
  }

  private static boolean isProtectedEndpoint(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/api/") || isWebSocket(request);
  }

  private static boolean requiresOrigin(HttpServletRequest request) {
    return !"GET".equals(request.getMethod()) || isWebSocket(request);
  }

  private static boolean requiresCsrf(HttpServletRequest request) {
    return !"GET".equals(request.getMethod()) && !isWebSocket(request);
  }

  private static boolean requiresBodyLimit(HttpServletRequest request) {
    return !"GET".equals(request.getMethod());
  }

  private static boolean hasAllowedContentLength(HttpServletRequest request) {
    long length = request.getContentLengthLong();
    return length >= 0 && length <= MAX_REQUEST_BYTES;
  }

  private static void requestTooLarge(HttpServletResponse response) throws IOException {
    response.setStatus(413);
    response.setContentType("application/json");
    response.getWriter().write("{\"message\":\"Request too large or missing content length\"}");
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
