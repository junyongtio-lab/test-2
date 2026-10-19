package com.universalremote.app;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/** Replays recorded alternate waveforms without synthesizing protocol frames. */
final class RecordedSignalSender {
    interface SignalConsumer { void transmit(int frequency, int[] pattern) throws Exception; }
    private final Map<String, Boolean> nextAlternate = new HashMap<>();

    /** Each successful ordinary press advances only this profile's alternating state. */
    synchronized void send(String profileId, String sourceKey, JSONObject commands, SignalConsumer consumer) throws Exception {
        if(profileId == null || profileId.isEmpty() || sourceKey == null || sourceKey.isEmpty())
            throw new IllegalArgumentException("Choose a recorded IR profile and button.");
        if(!commands.has(sourceKey))
            throw new IllegalArgumentException("This Mi remote profile does not include that button.");
        boolean explicitAlternate = sourceKey.endsWith("_r");
        String selected = sourceKey;
        if(!explicitAlternate && Boolean.TRUE.equals(nextAlternate.get(profileId)) && commands.has(sourceKey + "_r"))
            selected = sourceKey + "_r";
        JSONObject signal = commands.getJSONObject(selected);
        int[] pattern = IrCatalog.checkedPattern(signal);
        consumer.transmit(signal.getInt("carrierHz"), pattern);
        // Hardware/validation exceptions leave state intact; explicit source buttons are exact replays.
        if(!explicitAlternate) nextAlternate.put(profileId, !Boolean.TRUE.equals(nextAlternate.get(profileId)));
    }
}
