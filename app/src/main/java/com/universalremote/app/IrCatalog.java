package com.universalremote.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.ConsumerIrManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class IrCatalog {
    private final List<JSONObject> profiles = new ArrayList<>();
    private final ConsumerIrManager ir;
    private final SharedPreferences prefs;
    IrCatalog(Context context) throws Exception {
        ir = (ConsumerIrManager)context.getSystemService(Context.CONSUMER_IR_SERVICE);
        prefs = context.getSharedPreferences("ir_imports", Context.MODE_PRIVATE);
        try (InputStream in = context.getAssets().open("ir/profiles.json")) { add(new JSONObject(read(in))); }
        add(new JSONObject(prefs.getString("custom", "{\"profiles\":[]}")));
    }
    static String read(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (out.size() + count > 512 * 1024) throw new IllegalArgumentException("Profile file exceeds 512 KB.");
            out.write(buffer, 0, count);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    private void add(JSONObject root) throws Exception {
        JSONArray array = root.getJSONArray("profiles");
        for (int i=0;i<array.length();i++) profiles.add(array.getJSONObject(i));
    }
    List<JSONObject> forBrand(String brand) {
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject profile : profiles) if (brand.equalsIgnoreCase(profile.optString("brand"))) result.add(profile);
        return result;
    }
    JSONObject find(String id) {
        for (JSONObject profile : profiles) if (id.equals(profile.optString("id"))) return profile;
        return null;
    }
    boolean hasEmitter() { return ir != null && ir.hasIrEmitter(); }
    boolean supports(String id, String command) {
        JSONObject profile = find(id);
        return profile != null && profile.optJSONObject("commands") != null && profile.optJSONObject("commands").has(command);
    }
    void transmit(String profileId, String command) throws Exception {
        if (!hasEmitter()) throw new IllegalStateException("This phone has no infrared transmitter. Wi-Fi control requires a supported TV. iPhones cannot transmit infrared.");
        JSONObject profile = find(profileId);
        if (profile == null) throw new IllegalArgumentException("IR profile is missing. Import it again.");
        JSONObject code = profile.getJSONObject("commands").optJSONObject(command);
        if (code == null) throw new IllegalArgumentException("This remote profile does not include that button.");
        int frequency = code.getInt("carrierHz");
        int[] pattern = checkedPattern(code);
        ConsumerIrManager.CarrierFrequencyRange[] ranges = ir.getCarrierFrequencies();
        if (ranges != null && ranges.length > 0) {
            boolean match = false;
            for (ConsumerIrManager.CarrierFrequencyRange range : ranges)
                if (frequency >= range.getMinFrequency() && frequency <= range.getMaxFrequency()) match = true;
            if (!match) throw new IllegalStateException("Your phone does not support this IR carrier frequency.");
        }
        ir.transmit(frequency, pattern);
    }
    static int[] checkedPattern(JSONObject code) throws Exception {
        int hz = code.getInt("carrierHz");
        if (code.getDouble("carrierHz") != hz || hz < 20000 || hz > 60000) throw new IllegalArgumentException("IR frequency must be an integer between 20 and 60 kHz.");
        JSONArray array = code.getJSONArray("pattern");
        if (array.length() < 3 || array.length() > 2000) throw new IllegalArgumentException("Invalid IR pattern length.");
        int[] result = new int[array.length()]; long total=0;
        for (int i=0;i<result.length;i++) {
            double exact=array.getDouble(i); result[i]=array.getInt(i);
            if (exact != result[i] || result[i] < 1 || result[i] > 1000000) throw new IllegalArgumentException("IR timings must be positive integer microseconds.");
            total+=result[i];
        }
        if (total > 1900000) throw new IllegalArgumentException("IR transmission is too long (maximum 1.9 seconds).");
        return result;
    }
    int importProfiles(String data) throws Exception {
        JSONObject root = new JSONObject(data);
        JSONArray array = root.getJSONArray("profiles");
        if (array.length() == 0 || array.length() > 32) throw new IllegalArgumentException("Import 1 to 32 profiles at a time.");
        JSONArray old = new JSONObject(prefs.getString("custom", "{\"profiles\":[]}")).getJSONArray("profiles");
        List<JSONObject> validated = new ArrayList<>();
        for (int i=0;i<array.length();i++) {
            JSONObject p = array.getJSONObject(i);
            String brand = p.getString("brand");
            if (!brand.matches("(?i)Samsung|LG|Panasonic|Hisense|Astro")) throw new IllegalArgumentException("Choose one of the five supported brands.");
            if (p.getString("model").trim().isEmpty()) throw new IllegalArgumentException("Profile model is required.");
            JSONObject commands = p.getJSONObject("commands");
            if (commands.length() == 0 || commands.length() > 128) throw new IllegalArgumentException("A profile must contain 1 to 128 commands.");
            java.util.Iterator<String> keys = commands.keys();
            while (keys.hasNext()) checkedPattern(commands.getJSONObject(keys.next()));
            p.put("id", "import_" + java.util.UUID.randomUUID());
            p.put("hardwareTested", false);
            p.put("source", new JSONObject().put("license", "User-supplied learned profile").put("url", "Imported locally"));
            validated.add(p);
        }
        if (old.length() + validated.size() > 64) throw new IllegalArgumentException("Maximum of 64 imported profiles reached.");
        for (JSONObject p : validated) old.put(p);
        prefs.edit().putString("custom",new JSONObject().put("profiles",old).toString()).apply();
        profiles.addAll(validated);
        return validated.size();
    }
}
