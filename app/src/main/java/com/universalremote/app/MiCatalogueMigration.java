package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Selects obsolete infrared entries without touching Wi-Fi device metadata or credentials. */
final class MiCatalogueMigration {
    interface ProfileLookup { boolean contains(String id); }
    static final class Result {
        final JSONArray retained = new JSONArray();
        final List<String> removedIds = new ArrayList<>();
        int removedCount;
    }
    private MiCatalogueMigration() { }
    static Result removeUnavailable(JSONArray devices, ProfileLookup profiles) throws Exception {
        Result result = new Result();
        Set<String> retainedIds = new HashSet<>();
        for(int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.getJSONObject(i);
            String mode = device.optString("mode");
            if((mode.equals("ir") || mode.equals("ac")) && !profiles.contains(device.optString("profile"))) {
                result.removedCount++;
                String id = device.optString("id");
                if(!id.isEmpty()) result.removedIds.add(id);
            } else {
                result.retained.put(device);
                retainedIds.add(device.optString("id"));
            }
        }
        // Corrupt duplicate IDs must never delete credentials still used by a retained TV.
        result.removedIds.removeAll(retainedIds);
        return result;
    }
}
