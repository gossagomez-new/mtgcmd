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

  public SecurityFilter(final GuestSessions guests, final String origin) {
    this.guests = guests;
    this.origin = origin;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
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

  private static void writeSecurityHeaders(final HttpServletResponse response) {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "same-origin");
    response.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; img-src 'self' https://*.scryfall.io data:; connect-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
  }

  private boolean authorize(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException {
    if ("/api/session".equals(request.getRequestURI())) {
      return true;
    }
    try {
      final GuestSessions.Guest guest = guests.require(request);
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

  private static boolean isWebSocket(final HttpServletRequest request) {
    return "/ws".equals(request.getRequestURI());
  }

  private static boolean isProtectedEndpoint(final HttpServletRequest request) {
    return request.getRequestURI().startsWith("/api/") || isWebSocket(request);
  }

  private static boolean requiresOrigin(final HttpServletRequest request) {
    return !"GET".equals(request.getMethod()) || isWebSocket(request);
  }

  private static boolean requiresCsrf(final HttpServletRequest request) {
    return !"GET".equals(request.getMethod()) && !isWebSocket(request);
  }

  private static boolean requiresBodyLimit(final HttpServletRequest request) {
    return !"GET".equals(request.getMethod());
  }

  private static boolean hasAllowedContentLength(final HttpServletRequest request) {
    final long length = request.getContentLengthLong();
    return length >= 0 && length <= MAX_REQUEST_BYTES;
  }

  private static void requestTooLarge(final HttpServletResponse response) throws IOException {
    response.setStatus(413);
    response.setContentType("application/json");
    response.getWriter().write("{\"message\":\"Request too large or missing content length\"}");
  }

  private static boolean equal(final String expected, final String actual) {
    return actual != null
        && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }

  private static void forbidden(final HttpServletResponse response) throws IOException {
    response.setStatus(403);
    response.setContentType("application/json");
    response
        .getWriter()
        .write("{\"message\":\"Guest session, origin, or CSRF validation failed\"}");
  }
}
