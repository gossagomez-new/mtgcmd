package dev.mtgcmd.catalog;

import java.net.URI;
import java.util.Set;
import java.util.regex.*;

record MoxfieldDeckUrl(String deckId) {
  private static final Set<String> ALLOWED_HOSTS = Set.of("moxfield.com", "www.moxfield.com");
  private static final Pattern DECK_PATH = Pattern.compile("/decks/([A-Za-z0-9_-]{1,80})/?");

  static MoxfieldDeckUrl parse(String input) {
    URI uri;
    try {
      uri = URI.create(input.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid Moxfield URL", e);
    }
    Matcher path = DECK_PATH.matcher(uri.getPath() == null ? "" : uri.getPath());
    if (!usesHttps(uri) || !hasAllowedHost(uri) || !hasDefaultAuthority(uri) || !path.matches())
      throw new IllegalArgumentException("Use a public HTTPS Moxfield deck URL");
    return new MoxfieldDeckUrl(path.group(1));
  }

  URI apiUri() {
    return URI.create("https://api2.moxfield.com/v3/decks/all/" + deckId);
  }

  private static boolean usesHttps(URI uri) {
    return "https".equals(uri.getScheme());
  }

  private static boolean hasAllowedHost(URI uri) {
    return uri.getHost() != null && ALLOWED_HOSTS.contains(uri.getHost());
  }

  private static boolean hasDefaultAuthority(URI uri) {
    return uri.getUserInfo() == null && uri.getPort() == -1;
  }
}
