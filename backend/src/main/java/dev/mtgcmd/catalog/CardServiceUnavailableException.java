package dev.mtgcmd.catalog;

/** A card service could not fulfill the request. */
public final class CardServiceUnavailableException extends CardServiceException {
  public CardServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public CardServiceUnavailableException withMessage(String message) {
    return new CardServiceUnavailableException(message, this);
  }
}
