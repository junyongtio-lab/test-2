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
    static final String STB_SOURCE_URL = "https://sg-urc.io.mi.com";
    static final String APK_SOURCE_URL = "https://github.com/junyongtio-lab/test-2";
    static final String APK_SHA256 = "4c20655f2b636d86152202994da2a1b88f4ebf5bddf58b7ef6c2bf8e42fa5c73";
    static final String APK_ARCHIVE_SHA256 = "72fc2fc859255e19963370ecd3252889af3f8a2e609d2c74c2d995e03b4267a6";
    static final String APK_DATABASE_SHA256 = "727a271f25889d97fc94b021b240f62d1ce559ce8a98cf14b7ac0d3efb657bfa";
    static final String ARCHIVE_SHA256 = "44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895";
    static final String ARCHIVE_INDEX_SHA256 = "221bfefd1307b846b89093489a1a1676132fe4c4f1f516e39dfafd6bc71bc7ac";
    static final int PROFILE_LIMIT = 512 * 1024;
    private static final int INDEX_LIMIT = 16 * 1024 * 1024, CACHE_SIZE = 8;
    private static final int ARCHIVE = 0, STB = 1, APK = 2;
    interface AssetReader { InputStream open(String asset) throws Exception; }
    interface Emitter extends IrHardware.Probe { void transmit(int frequency,int[] pattern); }
    private final Map<String, JSONObject> profiles = new LinkedHashMap<>();
    private final Set<String> archiveProfileIds = new java.util.HashSet<>();
    private final Map<String,Integer> matchingRanks = new LinkedHashMap<>();
    private final LinkedHashMap<String, JSONObject> cache = new LinkedHashMap<>(CACHE_SIZE, .75f, true);
    private final ConsumerIrManager ir;
    private final AssetReader assets;
    private final JSONObject coverage;
    private final RecordedSignalSender recordedSender = new RecordedSignalSender();

    IrCatalog(Context context) throws Exception {
        this(readIndex(context, "ir/mi/index.json"), readIndex(context, "ir/mi_stb/index.json"),
            readIndex(context, "ir/mi_apk/index.json"), readIndex(context, "ir/matching_order.json"),
            asset -> context.getAssets().open(asset),
            (ConsumerIrManager) context.getSystemService(Context.CONSUMER_IR_SERVICE));
        // Only remove the superseded import value after every replacement index validates.
        context.getSharedPreferences("ir_imports", Context.MODE_PRIVATE).edit().remove("custom").apply();
    }
    IrCatalog(JSONObject index, AssetReader assets) throws Exception { this(index, null, null, null, assets, null); }
    IrCatalog(JSONObject index, JSONObject supplement, AssetReader assets) throws Exception {
        this(index, supplement, null, null, assets, null);
    }
    IrCatalog(JSONObject index, JSONObject supplement, JSONObject apk, JSONObject matchingOrder, AssetReader assets) throws Exception {
        this(index, supplement, apk, matchingOrder, assets, null);
    }
    private IrCatalog(JSONObject index, JSONObject supplement, JSONObject apk, JSONObject matchingOrder, AssetReader assets, ConsumerIrManager ir) throws Exception {
        addIndex(index, ARCHIVE);
        this.coverage = new JSONObject(index.getJSONObject("coverage").toString());
        if(supplement != null) addIndex(supplement, STB);
        if(apk != null) addIndex(apk, APK);
        if(matchingOrder != null) addMatchingOrder(matchingOrder);
        if(supplement != null || apk != null) mergeCoverage();
        this.assets = assets;
        this.ir = ir;
    }
    private void addIndex(JSONObject index, int kind) throws Exception {
        if(index.getDouble("schemaVersion") != 2) throw new IllegalArgumentException("Unsupported Mi catalogue version.");
        if(kind == STB) checkStbSource(index.getJSONObject("source"));
        else if(kind == APK) checkApkSource(index.getJSONObject("source"));
        else checkSource(index.getJSONObject("source"));
        JSONArray array = index.getJSONArray("profiles");
        int limit = kind == STB ? 64 : kind == APK ? 10000 : 50000;
        if(array.length() == 0 || array.length() > limit) throw new IllegalArgumentException("Invalid Mi catalogue size.");
        Set<String> supplementalModels = new java.util.HashSet<>();
        for(int i = 0; i < array.length(); i++) {
            JSONObject profile = array.getJSONObject(i);
            String id = profile.getString("id");
            if(!id.matches("mi_[a-f0-9]{8,64}") || profiles.containsKey(id)) throw new IllegalArgumentException("Invalid or duplicate Mi profile ID.");
            String directory = kind == STB ? "ir/mi_stb/profiles/" : kind == APK ? "ir/mi_apk/profiles/" : "ir/mi/profiles/";
            if(!profile.getString("asset").equals(directory + id + ".json")) throw new IllegalArgumentException("Invalid Mi profile asset path.");
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
            if(kind == STB) {
                String model = source.getString("modelId");
                if(!category.equals("set_top_box") || !profile.getString("brand").equals("Astro")
                    || !Boolean.FALSE.equals(profile.get("hardwareTested"))
                    || !(source.get("brandId") instanceof Number) || source.getDouble("brandId") != 3833 || !model.matches("kk_2_3833_[0-9]{1,12}")
                    || !supplementalModels.add(model)
                    || !profile.getString("model").equals(model)
                    || !source.getString("url").equals(STB_SOURCE_URL + "/app/public/get_stb_keyset")
                    || !source.getString("sourceFile").equals("tools/mi_stb/source/keyset_" + model + ".json")
                    || !source.getString("brandFile").equals("tools/mi_stb/source/brands_my.json"))
                    throw new IllegalArgumentException("Invalid Mi online Astro provenance.");
            } else if(kind == APK) {
                checkApkProfile(profile, source, supplementalModels);
            }
            profiles.put(id, profile);
            if(kind == ARCHIVE) archiveProfileIds.add(id);
        }
    }
    private static void checkApkProfile(JSONObject profile, JSONObject source, Set<String> rows) throws Exception {
        String table = source.getString("sourceTable"), model = source.getString("modelId"), category = profile.getString("category");
        Object brandValue = source.get("brandId"), deviceValue = source.get("sourceDeviceId");
        if(!table.equals("mi_remote_ircode") && !table.equals("kk_remote_ircode") && !table.equals("xm_remote_ircode"))
            throw new IllegalArgumentException("Invalid Mi APK source table.");
        if(!(brandValue instanceof String) || !((String)brandValue).matches("[0-9]{1,10}")
            || !(deviceValue instanceof Number) || (source.getDouble("sourceDeviceId") != 1 && source.getDouble("sourceDeviceId") != 3)
            || !category.equals(source.getInt("sourceDeviceId") == 1 ? "tv" : "air_conditioner")
            || !model.matches("[A-Za-z0-9_]{1,100}") || !rows.add(table + "/" + model)
            || !profile.getString("model").equals(model) || !Boolean.FALSE.equals(profile.get("hardwareTested"))
            || !source.getString("sourceFile").equals("irlocaldata.db/" + table + "/" + model)
            || !source.getString("brandFile").equals("irlocaldata.db/brand/" + brandValue)
            || !source.getString("brandSha256").matches("[a-f0-9]{64}")
            || !source.getString("format").equals(table.equals("xm_remote_ircode")
                ? "aes-gzip-microsecond-json-array" : "microsecond-json-array"))
            throw new IllegalArgumentException("Invalid Mi APK profile provenance.");
    }
    private void mergeCoverage() throws Exception {
        Set<String> allBrands = new java.util.HashSet<>();
        Map<String, Set<String>> categoryBrands = new LinkedHashMap<>();
        JSONObject categories = new JSONObject();int commands = 0;
        for(JSONObject profile : profiles.values()) {
            String category = profile.getString("category"), brand = profile.getString("brand");
            allBrands.add(brand);
            Set<String> names = categoryBrands.get(category);
            if(names == null) { names = new java.util.HashSet<>(); categoryBrands.put(category, names); }
            names.add(brand);
            JSONObject counts = categories.optJSONObject(category);
            if(counts == null) { counts = new JSONObject(); categories.put(category, counts); }
            counts.put("profiles", counts.optInt("profiles") + 1);
            counts.put("commands", counts.optInt("commands") + profile.getInt("commandCount"));
            counts.put("brands", names.size());
            commands += profile.getInt("commandCount");
        }
        coverage.put("profiles", profiles.size()).put("commands", commands)
            .put("brands", allBrands.size()).put("categories", categories);
        JSONArray absent = coverage.optJSONArray("requestedBrandsAbsent");
        if(absent != null) {
            JSONArray remaining = new JSONArray();
            for(int i = 0; i < absent.length(); i++) {
                String requested = absent.getString(i);boolean found = false;
                for(String brand : allBrands) if(brand.equalsIgnoreCase(requested)) { found = true; break; }
                if(!found) remaining.put(requested);
            }
            coverage.put("requestedBrandsAbsent", remaining);
        }
    }
    private static JSONObject readIndex(Context context, String path) throws Exception {
        try(InputStream in = context.getAssets().open(path)) {
            return new JSONObject(readBounded(in, INDEX_LIMIT));
        }
    }
    private static void checkStbSource(JSONObject source) throws Exception {
        if(!source.getString("repository").equals(STB_SOURCE_URL)
            || !source.getString("origin").equals(STB_SOURCE_URL)
            || !source.getString("kind").equals("mi-stb-public-api")
            || !source.getString("country").equals("MY"))
            throw new IllegalArgumentException("Unsupported Mi online catalogue source.");
    }
    private static void checkSource(JSONObject source) throws Exception {
        String url = source.optString("url", source.optString("repository"));
        if(!url.equals(SOURCE_URL) && !url.startsWith(SOURCE_URL + "/")) throw new IllegalArgumentException("Only the bundled Mi remote database is supported.");
    }
    private static void checkApkSource(JSONObject source) throws Exception {
        if(!source.getString("repository").equals(APK_SOURCE_URL)
            || !source.getString("kind").equals("mi-apk-bundled-database")
            || !source.getString("apkSha256").equals(APK_SHA256)
            || !source.getString("archiveSha256").equals(APK_ARCHIVE_SHA256)
            || !source.getString("frequencyUnit").equals("Hz") || !source.getString("timingUnit").equals("microseconds")
            || !source.getString("databaseSha256").equals(APK_DATABASE_SHA256)
            || !source.getString("versionName").equals("7.2.4M")
            || !(source.get("versionCode") instanceof Number) || source.getDouble("versionCode") != 6500)
            throw new IllegalArgumentException("Unsupported Mi APK database source.");
    }
    private void addMatchingOrder(JSONObject order) throws Exception {
        JSONObject source = order.getJSONObject("source");
        if(order.getDouble("schemaVersion") != 1 || !source.getString("repository").equals(SOURCE_URL)
            || !source.getString("release").equals("v2.1") || !source.getString("archiveSha256").equals(ARCHIVE_SHA256)
            || !source.getString("indexSha256").equals(ARCHIVE_INDEX_SHA256) || !source.getString("country").equals("MY"))
            throw new IllegalArgumentException("Unsupported Mi matching-order source.");
        JSONArray groups = order.getJSONArray("groups");
        if(groups.length() == 0 || groups.length() > archiveProfileIds.size())
            throw new IllegalArgumentException("Invalid Mi matching groups.");
        Set<String> groupsSeen = new java.util.HashSet<>();int rank = 0;
        for(int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            String brand = group.getString("brand"), file = group.getString("sourceBrandFile"), digest = group.getString("sourceBrandSha256");
            if(!group.getString("category").equals("tv") || !digest.matches("[a-f0-9]{64}") || !groupsSeen.add(file))
                throw new IllegalArgumentException("Invalid Mi matching group provenance.");
            JSONArray entries = group.getJSONArray("profiles");
            if(entries.length() == 0 || entries.length() > archiveProfileIds.size())
                throw new IllegalArgumentException("Invalid Mi matching group size.");
            for(int j = 0; j < entries.length(); j++) {
                JSONObject entry = entries.getJSONObject(j);String id = entry.getString("id");
                JSONObject metadata = profiles.get(id);
                if(metadata == null || !archiveProfileIds.contains(id) || matchingRanks.containsKey(id))
                    throw new IllegalArgumentException("Unknown or duplicate Mi matching profile.");
                JSONObject original = metadata.getJSONObject("source");
                if(!metadata.getString("category").equals("tv") || !metadata.getString("brand").equals(brand)
                    || !original.getString("brandFile").equals(file) || !original.getString("brandSha256").equals(digest)
                    || !original.getString("modelId").equals(entry.getString("modelId")))
                    throw new IllegalArgumentException("Mi matching profile source changed.");
                matchingRanks.put(id, rank++);
            }
        }
        for(String id : archiveProfileIds) {
            if(profiles.get(id).getString("category").equals("tv") && !matchingRanks.containsKey(id))
                throw new IllegalArgumentException("Mi matching order omitted a TV profile.");
        }
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
        if(category.equals("tv") && !matchingRanks.isEmpty()) java.util.Collections.sort(result, (left, right) -> {
            Integer a = matchingRanks.get(left.optString("id")), b = matchingRanks.get(right.optString("id"));
            return Integer.compare(a == null ? Integer.MAX_VALUE : a, b == null ? Integer.MAX_VALUE : b);
        });
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
        JSONObject commands = loadProfile(profileId).getJSONObject("commands");
        recordedSender.send(profileId, command, commands, this::transmitSignal);
    }
    void transmitSignal(int frequency, int[] pattern) throws Exception {
        transmitSignal(ir==null?null:new Emitter() {
            @Override public boolean hasEmitter(){return ir.hasIrEmitter();}
            @Override public int[][] carrierRanges(){
                ConsumerIrManager.CarrierFrequencyRange[] ranges=ir.getCarrierFrequencies();
                if(ranges==null)return null;
                int[][] values=new int[ranges.length][];
                for(int i=0;i<ranges.length;i++)if(ranges[i]!=null)values[i]=new int[]{ranges[i].getMinFrequency(),ranges[i].getMaxFrequency()};
                return values;
            }
            @Override public void transmit(int carrier,int[] timings){ir.transmit(carrier,timings);}
        },frequency,pattern);
    }
    static void transmitSignal(Emitter emitter,int frequency,int[] pattern) {
        if(emitter==null||!emitter.hasEmitter()) throw new IllegalStateException("This phone has no infrared transmitter. An IR-equipped Android phone is required.");
        validateSignal(frequency, pattern);
        int[][] ranges;
        // Android may confirm an emitter while omitting its optional carrier capabilities.
        // Only a complete, valid set of nonmatching ranges establishes unsupported frequency.
        try { ranges=emitter.carrierRanges(); }
        catch(RuntimeException unavailable){ranges=null;}
        if(ranges!=null&&ranges.length>0) {
            boolean match=false,unknown=false;
            for(int[] range:ranges) {
                if(range==null||range.length!=2||range[0]<=0||range[1]<range[0]){unknown=true;continue;}
                if(frequency>=range[0]&&frequency<=range[1])match=true;
            }
            if(!match&&!unknown)throw new IllegalStateException("Your phone does not support this IR carrier frequency.");
        }
        emitter.transmit(frequency,pattern);
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
