package dev.mtgcmd.game;

import java.util.*;

public final class Game {
  public String id = UUID.randomUUID().toString();
  public long revision;
  public String status = "lobby";
  public int host;
  public int active;
  public int turn = 1;
  public String phase = "Untap";
  public List<Player> players = new ArrayList<>();
  public List<Card> cards = new ArrayList<>();
  public List<Ability> abilities = new ArrayList<>();
  public Map<Integer, Set<Integer>> grants = new HashMap<>();
  public Map<Integer, Integer> searches = new HashMap<>();
  public List<String> log = new ArrayList<>();

  public static final class Player {
    public int seat;
    public String name;
    public boolean ready;
    public boolean acceptedWarnings;
    public long deckRevision;
    public boolean conceded;
    public List<Definition> deck = new ArrayList<>();
    public List<String> commanders = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
    public Map<String, Integer> counters = new LinkedHashMap<>(Map.of("life", 40, "poison", 0));
  }

  public record Face(String name, String image, String text, String type) {}

  public record Definition(
      String id,
      String name,
      String image,
      String text,
      String type,
      List<String> colors,
      boolean legal,
      List<Face> faces) {}

  public static final class Card {
    public String id = UUID.randomUUID().toString();
    public Definition definition;
    public int owner;
    public int controller;
    public int zoneOwner;
    public String zone;
    public boolean tapped;
    public boolean faceDown;
    public int face;
    public boolean token;
    public String commanderKey;
    public String attachedTo;
    public String combat = "";
    public long stackOrder;
    public double x;
    public double y;
    public Map<String, Integer> counters = new HashMap<>();
    public Set<Integer> revealedTo = new HashSet<>();
  }

  public static final class Ability {
    public String id = UUID.randomUUID().toString();
    public int controller;
    public String text;
    public long stackOrder;
  }

  public record Command(String id, long revision, String type, Map<String, Object> data) {}
}
