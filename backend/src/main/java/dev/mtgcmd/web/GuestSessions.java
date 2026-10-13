package dev.mtgcmd.web;

import dev.mtgcmd.store.RoomStore;
import jakarta.servlet.http.*;
import java.security.*;
import java.util.*;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;

public final class GuestSessions {
  public record Guest(String hash, String csrf) {}

  private final JdbcTemplate jdbc;
  private final boolean secure;
  private final SecureRandom random = new SecureRandom();

  public GuestSessions(JdbcTemplate jdbc, boolean secure) {
    this.jdbc = jdbc;
    this.secure = secure;
  }

  public Guest bootstrap(HttpServletRequest request, HttpServletResponse response) {
    Guest guest = find(request);
    if (guest != null) return guest;
    String token = secret();
    guest = new Guest(RoomStore.hash(token), secret());
    jdbc.update(
        "INSERT INTO guests(token_hash,csrf_token) VALUES (?,?)", guest.hash(), guest.csrf());
    response.addHeader(
        "Set-Cookie",
        ResponseCookie.from("table_guest", token)
            .path("/")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .maxAge(60L * 60 * 24 * 365)
            .build()
            .toString());
    return guest;
  }

  public Guest require(HttpServletRequest request) {
    Guest guest = find(request);
    if (guest == null) throw new RoomStore.Forbidden("Refresh to establish a guest session");
    return guest;
  }

  private Guest find(HttpServletRequest request) {
    if (request.getCookies() == null) return null;
    for (Cookie cookie : request.getCookies())
      if (cookie.getName().equals("table_guest") && cookie.getValue().matches("[a-f0-9]{64}")) {
        String hash = RoomStore.hash(cookie.getValue());
        List<String> rows =
            jdbc.queryForList(
                "SELECT csrf_token FROM guests WHERE token_hash=?", String.class, hash);
        if (!rows.isEmpty()) return new Guest(hash, rows.getFirst());
      }
    return null;
  }

  private String secret() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
