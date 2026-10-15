package dev.mtgcmd.catalog;

/** A card-service request exceeded its deadline. */
public final class CardServiceTimeoutException extends CardServiceException {
  public CardServiceTimeoutException(final String message, final Throwable cause) {
    super(message, cause);
  }

  @Override
  public CardServiceTimeoutException withMessage(final String message) {
    return new CardServiceTimeoutException(message, this);
  }
}
