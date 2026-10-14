package dev.mtgcmd.catalog;

/** A card-service operation was interrupted. */
public final class CardServiceInterruptedException extends CardServiceException {
  public CardServiceInterruptedException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public CardServiceInterruptedException withMessage(String message) {
    return new CardServiceInterruptedException(message, this);
  }
}
