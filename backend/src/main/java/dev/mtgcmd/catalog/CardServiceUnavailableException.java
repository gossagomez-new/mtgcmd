package dev.mtgcmd.catalog;

/** A card service could not fulfill the request. */
public final class CardServiceUnavailableException extends CardServiceException {
  public CardServiceUnavailableException(final String message, final Throwable cause) {
    super(message, cause);
  }

  @Override
  public CardServiceUnavailableException withMessage(final String message) {
    return new CardServiceUnavailableException(message, this);
  }
}
