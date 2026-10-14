package dev.mtgcmd.game;

import java.util.Map;

@FunctionalInterface
interface CommandHandler {
  String execute(Game game, int seat, Map<String, Object> data);
}
