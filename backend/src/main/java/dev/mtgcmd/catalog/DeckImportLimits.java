package dev.mtgcmd.catalog;

final class DeckImportLimits {
  static final int MAX_CARDS = 250;
  static final int MAX_NAME_LENGTH = 200;
  static final int MAX_TEXT_LENGTH = 100_000;
  static final String CARD_COUNT_ERROR = "Deck must contain 1–250 cards";

  private DeckImportLimits() {}

  static boolean validQuantity(int quantity) {
    return quantity >= 1 && quantity <= MAX_CARDS;
  }
}
