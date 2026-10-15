package dev.mtgcmd.catalog;

import dev.mtgcmd.game.Game.Definition;
import java.util.Optional;

/** Name-keyed definitions; cache access does not fetch providers or apply format policy. */
interface CardCache {
  /** Find a normalized name or previously stored alias; a miss is empty, not a provider request. */
  Optional<Definition> find(String name);

  /**
   * Persist a verified name-to-definition association; callers supply canonical names or aliases.
   */
  void store(String name, Definition definition);
}
