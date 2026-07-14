package com.cookiebuild.cookiedough.retention;

/** Keeps storage failures out of gameplay code and prevents partial fallback state. */
public final class PartyRepositoryException extends RuntimeException {
    public PartyRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public PartyRepositoryException(String message) {
        super(message);
    }
}
