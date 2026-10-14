package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.Game.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import java.util.*;

/** Object tabletop commands; revision and logging belong to the facade. */
final class ObjectActions {
  private final GameAccess access;

  ObjectActions(GameAccess access) {
    this.access = access;
  }

  String token(Game g, int seat, Map<String, Object> d) {

    String name = string(d, "name", 100),
        text = optionalString(d, "text", 2000),
        type = optionalString(d, "type", 200);
    require(g.cards.size() < 2000, "Table has too many objects");
    Card c = new Card();
    c.definition =
        new Definition(
            UUID.randomUUID().toString(), name, "", text, type, List.of(), true, List.of());
    c.owner = seat;
    c.controller = seat;
    c.zoneOwner = seat;
    c.zone = BATTLEFIELD;
    c.token = true;
    g.cards.add(c);

    return "token";
  }

  String editToken(Game g, int seat, Map<String, Object> d) {

    Card c = access.editable(g, seat, d);
    require(c.token, "Only token characteristics can be edited");
    c.definition =
        new Definition(
            c.definition.id(),
            string(d, "name", 100),
            c.definition.image(),
            optionalString(d, "text", 2000),
            optionalString(d, "type", 200),
            List.of(),
            true,
            List.of());

    return "edit token";
  }

  String copy(Game g, int seat, Map<String, Object> d) {

    Card source = find(g, string(d, "cardId", 80));
    require(access.canSeeDefinition(g, seat, source), "No permission to inspect this card");
    require(!isPrivate(source), "Copy a public card");
    require(g.cards.size() < 2000, "Table has too many objects");
    Card c = new Card();
    c.definition = source.definition;
    c.face = source.face;
    c.owner = seat;
    c.controller = seat;
    c.zoneOwner = seat;
    c.zone = BATTLEFIELD;
    c.token = true;
    g.cards.add(c);

    return "copy";
  }

  String ability(Game g, int seat, Map<String, Object> d) {

    require(g.abilities.size() < 200, "Stack is full");
    Ability a = new Ability();
    a.controller = seat;
    a.text = string(d, "text", 500);
    a.stackOrder = g.revision + 1;
    g.abilities.add(a);

    return "ability";
  }

  String resolveAbility(Game g, int seat, Map<String, Object> d) {

    String id = string(d, "abilityId", 80);
    Ability a =
        g.abilities.stream()
            .filter(x -> x.id.equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown ability"));
    require(a.controller == seat, "No permission to resolve this ability");
    g.abilities.remove(a);

    return "resolve ability";
  }
}
