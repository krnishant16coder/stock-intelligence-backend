package com.stockintelligence.common;

/** Transient failure (timeout, 5xx, IO) — safe to retry. */
public class TransientProviderException extends RuntimeException {
    public TransientProviderException(String message, Throwable cause) { super(message, cause); }
}
