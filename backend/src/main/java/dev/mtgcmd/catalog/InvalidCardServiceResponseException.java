package dev.mtgcmd.catalog;

/** A card service returned an unusable response. */
public final class InvalidCardServiceResponseException extends CardServiceException {
  public InvalidCardServiceResponseException(final String message, final Throwable cause) {
    super(message, cause);
  }

  @Override
  public InvalidCardServiceResponseException withMessage(final String message) {
    return new InvalidCardServiceResponseException(message, this);
  }
}
