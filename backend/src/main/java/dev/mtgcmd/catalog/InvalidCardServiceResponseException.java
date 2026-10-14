package dev.mtgcmd.catalog;

/** A card service returned an unusable response. */
public final class InvalidCardServiceResponseException extends CardServiceException {
  public InvalidCardServiceResponseException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public InvalidCardServiceResponseException withMessage(String message) {
    return new InvalidCardServiceResponseException(message, this);
  }
}
