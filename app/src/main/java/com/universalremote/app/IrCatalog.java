package com.universalremote.app;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** A small metadata index and an eight-profile cache of the bundled Mi JSON signals. */
final class IrCatalog {
    static final String SOURCE_URL = "https://github.com/ysard/mi_remote_database";
    static final int PROFILE_LIMIT = 512 * 1024;
    private static final int INDEX_LIMIT = 16 * 1024 * 1024, CACHE_SIZE = 8;
    interface AssetReader { InputStream open(String asset) throws Exception; }
    private final Map<String, JSONObject> profiles = new LinkedHashMap<>();
    private final LinkedHashMap<String, JSONObject> cache = new LinkedHashMap<>(CACHE_SIZE, .75f, true);
    private final ConsumerIrManager ir;
    private final AssetReader assets;
    private final JSONObject coverage;

    IrCatalog(Context context) throws Exception {
        this(readIndex(context), asset -> context.getAssets().open(asset),
            (ConsumerIrManager) context.getSystemService(Context.CONSUMER_IR_SERVICE));
        // Only remove the superseded import value after the replacement index validates.
        context.getSharedPreferences("ir_imports", Context.MODE_PRIVATE).edit().remove("custom").apply();
    }
    IrCatalog(JSONObject index, AssetReader assets) throws Exception { this(index, assets, null); }
    private IrCatalog(JSONObject index, AssetReader assets, ConsumerIrManager ir) throws Exception {
        if(index.getDouble("schemaVersion") != 2) throw new IllegalArgumentException("Unsupported Mi catalogue version.");
        checkSource(index.getJSONObject("source"));
        JSONArray array = index.getJSONArray("profiles");
        if(array.length() == 0 || array.length() > 50000) throw new IllegalArgumentException("Invalid Mi catalogue size.");
        for(int i = 0; i < array.length(); i++) {
            JSONObject profile = array.getJSONObject(i);
            String id = profile.getString("id");
            if(!id.matches("mi_[a-f0-9]{8,64}") || profiles.containsKey(id)) throw new IllegalArgumentException("Invalid or duplicate Mi profile ID.");
            if(!profile.getString("asset").equals("ir/mi/profiles/" + id + ".json")) throw new IllegalArgumentException("Invalid Mi profile asset path.");
            if(!profile.getString("assetSha256").matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid Mi signal asset digest.");
            String category = profile.getString("category");
            if(!category.equals("tv") && !category.equals("set_top_box") && !category.equals("air_conditioner") && !category.equals("fan"))
                throw new IllegalArgumentException("Invalid Mi device category.");
            checkedText(profile.getString("brand"), 160, "brand");
            checkedText(profile.getString("model"), 240, "model");
            int commands = profile.getInt("commandCount");
            if(profile.getDouble("commandCount") != commands || commands < 1 || commands > 10000)
                throw new IllegalArgumentException("Invalid Mi command count.");
            if(profile.optBoolean("hardwareTested")) throw new IllegalArgumentException("Unverified Mi profiles cannot claim hardware testing.");
            JSONObject source = profile.getJSONObject("source");
            checkedText(source.getString("sourceFile"), 500, "source file");
            checkedText(source.getString("brandFile"), 240, "brand file");
            checkedText(source.getString("modelId"), 240, "source model");
            if(!source.getString("sourceSha256").matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid Mi source digest.");
            profiles.put(id, profile);
        }
        this.coverage = index.getJSONObject("coverage");
        this.assets = assets;
        this.ir = ir;
    }
    private static JSONObject readIndex(Context context) throws Exception {
        try(InputStream in = context.getAssets().open("ir/mi/index.json")) {
            return new JSONObject(readBounded(in, INDEX_LIMIT));
        }
    }
    private static void checkSource(JSONObject source) throws Exception {
        String url = source.optString("url", source.optString("repository"));
        if(!url.equals(SOURCE_URL) && !url.startsWith(SOURCE_URL + "/")) throw new IllegalArgumentException("Only the bundled Mi remote database is supported.");
    }
    private static void checkedText(String value, int limit, String field) {
        if(value.trim().isEmpty() || value.length() > limit || value.matches("(?s).*[\\p{Cntrl}].*"))
            throw new IllegalArgumentException("Invalid Mi " + field + ".");
    }
    static String read(InputStream input) throws Exception { return readBounded(input, PROFILE_LIMIT); }
    static String readBounded(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int count;
        while((count = input.read(buffer)) != -1) {
            if(out.size() + count > limit) throw new IllegalArgumentException("Mi JSON exceeds its size limit (profiles: 512 KB).");
            out.write(buffer, 0, count);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    List<JSONObject> forBrand(String brand) { return forBrand(brand, "tv"); }
    List<JSONObject> forBrand(String brand, String category) {
        List<JSONObject> result = new ArrayList<>();
        for(JSONObject profile : profiles.values())
            if(brand.equalsIgnoreCase(profile.optString("brand")) && category.equals(profile.optString("category"))) result.add(profile);
        return result;
    }
    Set<String> brands(String category) {
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for(JSONObject profile : profiles.values()) if(category.equals(profile.optString("category"))) result.add(profile.optString("brand"));
        return result;
    }
    JSONObject find(String id) {
        synchronized(cache) { JSONObject loaded = cache.get(id); if(loaded != null) return loaded; }
        return profiles.get(id);
    }
    boolean isLoaded(String id) { synchronized(cache) { return cache.containsKey(id); } }
    int profileCount() { return profiles.size(); }
    JSONObject coverage() { return coverage; }

    /** Call from a worker. No full signal catalogue is retained on the UI thread. */
    JSONObject loadProfile(String id) throws Exception {
        synchronized(cache) { JSONObject loaded = cache.get(id); if(loaded != null) return loaded; }
        JSONObject metadata = profiles.get(id);
        if(metadata == null) throw new IllegalArgumentException("Choose a profile from the Mi remote database.");
        String json;
        try(InputStream in = assets.open(metadata.getString("asset"))) { json = read(in); }
        if(!sha256(json).equals(metadata.getString("assetSha256"))) throw new IllegalArgumentException("Mi signal asset digest does not match its index.");
        JSONObject data = new JSONObject(json);
        if(!data.getString("sourceModelId").equals(metadata.getJSONObject("source").getString("modelId")))
            throw new IllegalArgumentException("Mi signal source does not match its index.");
        JSONObject commands = data.getJSONObject("commands");
        if(commands.length() != metadata.getInt("commandCount")) throw new IllegalArgumentException("Mi signal count does not match its index.");
        java.util.Iterator<String> keys = commands.keys();
        while(keys.hasNext()) {
            String key = keys.next(); checkedText(key, 240, "command key");
            JSONObject command = commands.getJSONObject(key);
            if(!key.equals(command.getString("sourceKey"))) throw new IllegalArgumentException("Mi source command key changed.");
            checkedText(command.getString("label"), 500, "command label");
            checkedPattern(command);
        }
        JSONObject loaded = new JSONObject(metadata.toString()).put("commands", commands);
        synchronized(cache) {
            cache.put(id, loaded);
            while(cache.size() > CACHE_SIZE) cache.remove(cache.keySet().iterator().next());
        }
        return loaded;
    }
    static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        char[] hex = new char[digest.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for(int i = 0; i < digest.length; i++) { hex[i * 2] = digits[(digest[i] & 255) >>> 4]; hex[i * 2 + 1] = digits[digest[i] & 15]; }
        return new String(hex);
    }
    boolean hasEmitter() { return ir != null && ir.hasIrEmitter(); }
    boolean supports(String id, String command) {
        synchronized(cache) {
            JSONObject profile = cache.get(id);
            return profile != null && profile.optJSONObject("commands").has(command);
        }
    }
    void transmit(String profileId, String command) throws Exception {
        if(!hasEmitter()) throw new IllegalStateException("This phone has no infrared transmitter. An IR-equipped Android phone is required.");
        JSONObject code = loadProfile(profileId).getJSONObject("commands").optJSONObject(command);
        if(code == null) throw new IllegalArgumentException("This Mi remote profile does not include that button.");
        transmitSignal(code.getInt("carrierHz"), checkedPattern(code));
    }
    void transmitSignal(int frequency, int[] pattern) throws Exception {
        if(!hasEmitter()) throw new IllegalStateException("This phone has no infrared transmitter. An IR-equipped Android phone is required.");
        validateSignal(frequency, pattern);
        ConsumerIrManager.CarrierFrequencyRange[] ranges = ir.getCarrierFrequencies();
        if(ranges != null && ranges.length > 0) {
            boolean match = false;
            for(ConsumerIrManager.CarrierFrequencyRange range : ranges)
                if(frequency >= range.getMinFrequency() && frequency <= range.getMaxFrequency()) match = true;
            if(!match) throw new IllegalStateException("Your phone does not support this IR carrier frequency.");
        }
        ir.transmit(frequency, pattern);
    }
    static void validateSignal(int frequency, int[] pattern) {
        if(frequency < 20000 || frequency > 60000) throw new IllegalArgumentException("IR frequency must be between 20 and 60 kHz.");
        if(pattern == null || pattern.length < 3 || pattern.length > 2000) throw new IllegalArgumentException("Invalid IR pattern length.");
        long total = 0;
        for(int duration : pattern) { if(duration < 1 || duration > 1000000) throw new IllegalArgumentException("Invalid IR timing."); total += duration; }
        if(total > 1900000) throw new IllegalArgumentException("IR transmission is too long (maximum 1.9 seconds).");
    }
    static int[] checkedPattern(JSONObject code) throws Exception {
        int hz = code.getInt("carrierHz");
        if(code.getDouble("carrierHz") != hz || hz < 20000 || hz > 60000) throw new IllegalArgumentException("IR frequency must be an integer between 20 and 60 kHz.");
        JSONArray array = code.getJSONArray("pattern");
        if(array.length() < 3 || array.length() > 2000) throw new IllegalArgumentException("Invalid IR pattern length.");
        int[] result = new int[array.length()];
        for(int i = 0; i < result.length; i++) {
            double exact = array.getDouble(i); result[i] = array.getInt(i);
            if(exact != result[i] || result[i] < 1 || result[i] > 1000000) throw new IllegalArgumentException("IR timings must be positive integer microseconds.");
        }
        validateSignal(hz, result);
        return result;
    }
}
