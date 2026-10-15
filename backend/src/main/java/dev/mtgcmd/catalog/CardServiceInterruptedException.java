package dev.mtgcmd.catalog;

/** A card-service operation was interrupted. */
public final class CardServiceInterruptedException extends CardServiceException {
  public CardServiceInterruptedException(final String message, final Throwable cause) {
    super(message, cause);
  }

  @Override
  public CardServiceInterruptedException withMessage(final String message) {
    return new CardServiceInterruptedException(message, this);
  }
}
