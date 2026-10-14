package dev.mtgcmd.catalog;

/** A card-service request exceeded its deadline. */
public final class CardServiceTimeoutException extends CardServiceException {
  public CardServiceTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public CardServiceTimeoutException withMessage(String message) {
    return new CardServiceTimeoutException(message, this);
  }
}
