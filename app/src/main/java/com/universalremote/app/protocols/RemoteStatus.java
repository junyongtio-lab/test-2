package com.universalremote.app.protocols;

public final class RemoteStatus {
    public enum Phase { DISCONNECTED, CONNECTING, PAIRING, READY, ERROR }

    private final Phase phase;
    private final String message;

    public RemoteStatus(Phase phase, String message) {
        this.phase = phase;
        this.message = message;
    }

    public Phase getPhase() { return phase; }
    public String getMessage() { return message; }
}
