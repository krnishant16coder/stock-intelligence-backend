package com.stockintelligence.common;

/** Thrown when an external data provider call fails after retries. Never leaks secrets. */
public class ExternalProviderException extends RuntimeException {
    public ExternalProviderException(String message) { super(message); }
    public ExternalProviderException(String message, Throwable cause) { super(message, cause); }
}
