package dev.mtgcmd.catalog;

/** Operational card-service failure with a player-facing message and retained cause. */
public abstract class CardServiceException extends IllegalArgumentException {
  protected CardServiceException(String message, Throwable cause) {
    super(message, cause);
  }

  /** Add adapter context while retaining the concrete failure category and original chain. */
  public abstract CardServiceException withMessage(String message);
}
