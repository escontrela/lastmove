package com.escontrela.lastmove.application.event;

/**
 * Published when a PGN import fails, so the UI can display an appropriate error message.
 */
public class PgnImportFailedEvent {

    private final String reason;
    private final Throwable cause;

    public PgnImportFailedEvent(String reason, Throwable cause) {
        this.reason = reason;
        this.cause = cause;
    }

    public PgnImportFailedEvent(String reason) {
        this(reason, null);
    }

    public String getReason() {
        return reason;
    }

    public java.util.Optional<Throwable> getCause() {
        return java.util.Optional.ofNullable(cause);
    }
}
