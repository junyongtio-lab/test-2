package com.universalremote.app;

import java.util.LinkedHashSet;
import java.util.Set;

/** User-observed responses belong only to the current candidate and sent button. */
final class TvTestSession {
    private final Set<String> confirmed = new LinkedHashSet<>();
    private String pending;
    void reset() { confirmed.clear(); pending = null; }
    void sending() { pending = null; }
    void sent(String key) { pending = key; }
    boolean awaitingResponse() { return pending != null; }
    void confirm() { if(pending != null) confirmed.add(pending); pending = null; }
    int count() { return confirmed.size(); }
    boolean canSave() { return count() >= 2; }
}
