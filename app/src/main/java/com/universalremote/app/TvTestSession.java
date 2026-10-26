package com.universalremote.app;

import java.util.LinkedHashSet;
import java.util.Set;

/** Shared recorded-remote confirmation: responses belong only to the current candidate and sent button. */
final class TvTestSession {
    private final Set<String> confirmed = new LinkedHashSet<>();
    private String pending;
    private int required = 2;
    void reset() { reset(2); }
    void reset(int distinctButtons) {
        if(distinctButtons < 1 || distinctButtons > 2) throw new IllegalArgumentException("Confirm one or two recorded buttons.");
        required = distinctButtons; confirmed.clear(); pending = null;
    }
    int requiredCount() { return required; }
    void sending() { pending = null; }
    void sent(String key) { pending = key; }
    boolean awaitingResponse() { return pending != null; }
    void confirm() { if(pending != null) confirmed.add(pending); pending = null; }
    int count() { return confirmed.size(); }
    boolean canSave() { return count() >= required; }
}
