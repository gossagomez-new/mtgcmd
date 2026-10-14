package dev.mtgcmd.game;

public enum GameFormat {
  COMMANDER(4, 40, 1, 250, true),
  STANDARD(2, 20, 60, 250, false);

  private final int seatCapacity;
  private final int startingLife;
  private final int minimumDeckSize;
  private final int maximumDeckSize;
  private final boolean commanders;

  GameFormat(
      int seatCapacity,
      int startingLife,
      int minimumDeckSize,
      int maximumDeckSize,
      boolean commanders) {
    this.seatCapacity = seatCapacity;
    this.startingLife = startingLife;
    this.minimumDeckSize = minimumDeckSize;
    this.maximumDeckSize = maximumDeckSize;
    this.commanders = commanders;
  }

  public int seatCapacity() {
    return seatCapacity;
  }

  public int startingLife() {
    return startingLife;
  }

  public int minimumDeckSize() {
    return minimumDeckSize;
  }

  public int maximumDeckSize() {
    return maximumDeckSize;
  }

  public boolean usesCommanders() {
    return commanders;
  }
}
