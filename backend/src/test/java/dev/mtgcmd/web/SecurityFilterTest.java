package dev.mtgcmd.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.mtgcmd.store.RoomStore;
import dev.mtgcmd.store.RoomStore.Forbidden;
import jakarta.servlet.FilterChain;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityFilterTest {
  private static final String ROOMS_PATH = "/api/rooms";

  private static final String GET_METHOD = "GET";

  private static final String ORIGIN_HEADER = "Origin";

  private static final String POST_METHOD = "POST";

  private static final String CSRF_HEADER = "X-CSRF-Token";

  private static final String ORIGIN = "http://table.test";
  private static final String CSRF = "secret";
  private static final String FORBIDDEN =
      "{\"message\":\"Guest session, origin, or CSRF validation failed\"}";
  private static final String TOO_LARGE =
      "{\"message\":\"Request too large or missing content length\"}";

  private final GuestSessions guests = mock(GuestSessions.class);
  private final SecurityFilter filter = new SecurityFilter(guests, ORIGIN);
  private final FilterChain chain = mock(FilterChain.class);
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  private MockHttpServletRequest request(
      final String method, final String path, final long length) {
    final MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.addHeader(ORIGIN_HEADER, ORIGIN);
    request.addHeader(CSRF_HEADER, CSRF);
    if (length >= 0) {
      request.setContent(new byte[(int) length]);
    }
    when(guests.require(request)).thenReturn(new GuestSessions.Guest("guest", CSRF));
    return request;
  }

  private void assertHeaders(final boolean protectedPath) {
    assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getHeader("Referrer-Policy")).isEqualTo("same-origin");
    assertThat(response.getHeader("Content-Security-Policy"))
        .isEqualTo(
            "default-src 'self'; img-src 'self' https://*.scryfall.io data:; connect-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
    assertThat(response.getHeader("Cache-Control")).isEqualTo(protectedPath ? "no-store" : null);
  }

  record PathCase(String method, String path, boolean protectedPath, boolean guestRequired) {}

  static Stream<PathCase> paths() {
    return Stream.of(GET_METHOD, POST_METHOD, "HEAD", "OPTIONS", "PUT", "DELETE", "PATCH")
        .flatMap(
            method ->
                Stream.of(
                    new PathCase(method, "/", false, false),
                    new PathCase(method, "/assets/app.js", false, false),
                    new PathCase(method, "/api", false, false),
                    new PathCase(method, "/ws/", false, false),
                    new PathCase(method, "/api/session", true, false),
                    new PathCase(method, "/api/session/", true, true),
                    new PathCase(method, ROOMS_PATH, true, true),
                    new PathCase(method, "/ws", true, true)));
  }

  @ParameterizedTest
  @MethodSource("paths")
  void acceptedMethodAndPathMatrixPreservesHeadersAndSessionChecks(final PathCase scenario)
      throws Exception {
    final MockHttpServletRequest request = request(scenario.method(), scenario.path(), 0);
    filter.doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(200);
    assertHeaders(scenario.protectedPath());
    verify(chain).doFilter(request, response);
    if (scenario.guestRequired()) {
      verify(guests).require(request);
    } else {
      verifyNoInteractions(guests);
    }
  }

  @ParameterizedTest
  @CsvSource({"/,false", "/api/session,true"})
  void exemptionsBypassCredentialsAndLength(final String path, final boolean protectedPath)
      throws Exception {
    final MockHttpServletRequest request = request(POST_METHOD, path, -1);
    request.removeHeader(ORIGIN_HEADER);
    request.removeHeader(CSRF_HEADER);
    when(guests.require(request)).thenThrow(new RoomStore.Forbidden("missing"));
    filter.doFilter(request, response, chain);
    assertHeaders(protectedPath);
    verifyNoInteractions(guests);
    verify(chain).doFilter(request, response);
  }

  @ParameterizedTest
  @CsvSource({"/api/rooms,GET", "/ws,GET", "/api/rooms,POST"})
  void missingGuestReturnsForbiddenBeforeOtherChecks(final String path, final String method)
      throws Exception {
    final MockHttpServletRequest request = request(method, path, -1);
    request.removeHeader(ORIGIN_HEADER);
    request.removeHeader(CSRF_HEADER);
    when(guests.require(request)).thenThrow(new RoomStore.Forbidden("missing"));
    filter.doFilter(request, response, chain);
    assertRejected(403, FORBIDDEN);
  }

  @ParameterizedTest
  @CsvSource({
    "POST,/api/rooms,Origin",
    "POST,/api/rooms,X-CSRF-Token",
    "GET,/ws,Origin",
    "POST,/ws,Origin",
    "HEAD,/api/rooms,Origin",
    "OPTIONS,/api/rooms,X-CSRF-Token"
  })
  void missingCredentialIsRejectedBeforeInvalidLength(
      final String method, final String path, final String header) throws Exception {
    final MockHttpServletRequest request = request(method, path, -1);
    request.removeHeader(header);
    filter.doFilter(request, response, chain);
    assertRejected(403, FORBIDDEN);
  }

  @ParameterizedTest
  @CsvSource({ORIGIN_HEADER, CSRF_HEADER})
  void mismatchedCredentialIsRejected(final String header) throws Exception {
    final MockHttpServletRequest request = request(POST_METHOD, ROOMS_PATH, 0);
    request.removeHeader(header);
    request.addHeader(header, "wrong");
    filter.doFilter(request, response, chain);
    assertRejected(403, FORBIDDEN);
  }

  @Test
  void getDoesNotRequireOriginCsrfOrLength() throws Exception {
    final MockHttpServletRequest request = request(GET_METHOD, ROOMS_PATH, -1);
    request.removeHeader(ORIGIN_HEADER);
    request.removeHeader(CSRF_HEADER);
    filter.doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
  }

  @ParameterizedTest
  @CsvSource({"GET,-1,200", "POST,-1,413", "POST,0,200", "POST,100000,200", "POST,100001,413"})
  void webSocketBypassesCsrfButRetainsNonGetLengthLimit(
      final String method, final long length, final int status) throws Exception {
    final MockHttpServletRequest request = request(method, "/ws", length);
    request.removeHeader(CSRF_HEADER);
    filter.doFilter(request, response, chain);
    if (status == 413) {
      assertRejected(status, TOO_LARGE);
    } else {
      verify(chain).doFilter(request, response);
    }
    assertThat(response.getStatus()).isEqualTo(status);
  }

  @ParameterizedTest
  @CsvSource({"-1,413", "0,200", "100000,200", "100001,413"})
  void apiLengthBoundariesAreInclusive(final long length, final int status) throws Exception {
    final MockHttpServletRequest request = request(POST_METHOD, ROOMS_PATH, length);
    filter.doFilter(request, response, chain);
    if (status == 413) {
      assertRejected(status, TOO_LARGE);
    } else {
      verify(chain).doFilter(request, response);
    }
    assertThat(response.getStatus()).isEqualTo(status);
  }

  @Test
  void unexpectedGuestFailurePropagatesWithoutContinuing() {
    final MockHttpServletRequest request = request(POST_METHOD, ROOMS_PATH, 0);
    final IllegalStateException failure = new IllegalStateException("database failed");
    when(guests.require(request)).thenThrow(failure);
    assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isSameAs(failure);
    verifyNoInteractions(chain);
    assertThat(response.getContentAsByteArray()).isEmpty();
    assertHeaders(true);
  }

  @Test
  void downstreamForbiddenIsNotTranslated() throws Exception {
    final MockHttpServletRequest request = request(GET_METHOD, ROOMS_PATH, -1);
    final Forbidden failure = new RoomStore.Forbidden("downstream");
    doThrow(failure).when(chain).doFilter(request, response);
    assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isSameAs(failure);
    assertThat(response.getContentAsByteArray()).isEmpty();
  }

  @Test
  void downstreamIoFailurePropagates() throws Exception {
    final MockHttpServletRequest request = request(GET_METHOD, ROOMS_PATH, -1);
    final IOException failure = new IOException("downstream");
    doThrow(failure).when(chain).doFilter(request, response);
    assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isSameAs(failure);
  }

  private void assertRejected(final int status, final String body) throws Exception {
    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(response.getContentAsString()).isEqualTo(body);
    assertHeaders(true);
    verifyNoInteractions(chain);
  }
}
