package dev.mtgcmd.game;

import static dev.mtgcmd.game.CommandInput.*;
import static dev.mtgcmd.game.GameLookup.*;
import static dev.mtgcmd.game.GameZones.*;

import dev.mtgcmd.game.Game.Ability;
import dev.mtgcmd.game.Game.Card;
import dev.mtgcmd.game.Game.Definition;
import java.util.*;

/** Object tabletop commands; revision and logging belong to the facade. */
final class ObjectActions {
  private final GameAccess access;

  ObjectActions(final GameAccess access) {
    this.access = access;
  }

  String token(final Game game, final int seat, final Map<String, Object> data) {

    final String name = string(data, "name", 100),
        text = optionalString(data, "text", 2000),
        type = optionalString(data, "type", 200);
    require(game.cards.size() < 2000, "Table has too many objects");
    final Card card = new Card();
    card.definition =
        new Definition(
            UUID.randomUUID().toString(), name, "", text, type, List.of(), true, List.of());
    card.owner = seat;
    card.controller = seat;
    card.zoneOwner = seat;
    card.zone = BATTLEFIELD;
    card.token = true;
    game.cards.add(card);

    return "token";
  }

  String editToken(final Game game, final int seat, final Map<String, Object> data) {

    final Card card = access.editable(game, seat, data);
    require(card.token, "Only token characteristics can be edited");
    card.definition =
        new Definition(
            card.definition.id(),
            string(data, "name", 100),
            card.definition.image(),
            optionalString(data, "text", 2000),
            optionalString(data, "type", 200),
            List.of(),
            true,
            List.of());

    return "edit token";
  }

  String copy(final Game game, final int seat, final Map<String, Object> data) {

    final Card source = find(game, string(data, "cardId", 80));
    require(access.canSeeDefinition(game, seat, source), "No permission to inspect this card");
    require(!isPrivate(source), "Copy a public card");
    require(game.cards.size() < 2000, "Table has too many objects");
    final Card card = new Card();
    card.definition = source.definition;
    card.face = source.face;
    card.owner = seat;
    card.controller = seat;
    card.zoneOwner = seat;
    card.zone = BATTLEFIELD;
    card.token = true;
    game.cards.add(card);

    return "copy";
  }

  String ability(final Game game, final int seat, final Map<String, Object> data) {

    require(game.abilities.size() < 200, "Stack is full");
    final Ability ability = new Ability();
    ability.controller = seat;
    ability.text = string(data, "text", 500);
    ability.stackOrder = game.revision + 1;
    game.abilities.add(ability);

    return "ability";
  }

  String resolveAbility(final Game game, final int seat, final Map<String, Object> data) {

    final String identifier = string(data, "abilityId", 80);
    final Ability ability =
        game.abilities.stream()
            .filter(x -> x.id.equals(identifier))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown ability"));
    require(ability.controller == seat, "No permission to resolve this ability");
    game.abilities.remove(ability);

    return "resolve ability";
  }
}
