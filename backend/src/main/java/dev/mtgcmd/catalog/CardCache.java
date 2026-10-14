package dev.mtgcmd.catalog;

import dev.mtgcmd.game.Game.Definition;
import java.util.Optional;

interface CardCache {
  Optional<Definition> find(String name);

  void store(String name, Definition definition);
}
