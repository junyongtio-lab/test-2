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
import java.util.Set;
import java.util.TreeSet;

final class IrCatalog {
    private final List<JSONObject> profiles = new ArrayList<>();
    private final ConsumerIrManager ir;
    private final SharedPreferences prefs;
    IrCatalog(Context context) throws Exception {
        ir = (ConsumerIrManager)context.getSystemService(Context.CONSUMER_IR_SERVICE);
        prefs = context.getSharedPreferences("ir_imports", Context.MODE_PRIVATE);
        for(String asset:new String[]{"ir/profiles.json","ir/tv_extra/profiles.json","ir/ac_captures/profiles.json"}) {
            try (InputStream in = context.getAssets().open(asset)) { add(new JSONObject(readBounded(in,8*1024*1024))); }
        }
        try (InputStream in = context.getAssets().open("astro_ultrabox.json")) {
            add(AstroProfiles.fromSource(new JSONObject(readBounded(in,512*1024))));
        }
        add(new JSONObject(prefs.getString("custom", "{\"profiles\":[]}")));
    }
    static String read(InputStream input) throws Exception {
        return readBounded(input,512*1024);
    }
    static String readBounded(InputStream input,int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (out.size() + count > limit) throw new IllegalArgumentException("Profile file exceeds its size limit (imports: 512 KB).");
            out.write(buffer, 0, count);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    private void add(JSONObject root) throws Exception {
        JSONArray array = root.getJSONArray("profiles");
        for (int i=0;i<array.length();i++) profiles.add(array.getJSONObject(i));
    }
    List<JSONObject> forBrand(String brand) {
        return forBrand(brand,"tv");
    }
    List<JSONObject> forBrand(String brand,String category) {
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject profile : profiles) if (brand.equalsIgnoreCase(profile.optString("brand")) && category.equals(profile.optString("category","tv"))) result.add(profile);
        return result;
    }
    Set<String> brands(String category) {
        Set<String> result=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for(JSONObject profile:profiles)if(category.equals(profile.optString("category","tv")))result.add(profile.optString("brand"));
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
        transmitSignal(code.getInt("carrierHz"),checkedPattern(code));
    }
    void transmitSignal(int frequency,int[] pattern) throws Exception {
        if (!hasEmitter()) throw new IllegalStateException("This phone has no infrared transmitter. An IR-equipped Android phone is required.");
        validateSignal(frequency,pattern);
        ConsumerIrManager.CarrierFrequencyRange[] ranges = ir.getCarrierFrequencies();
        if (ranges != null && ranges.length > 0) {
            boolean match = false;
            for (ConsumerIrManager.CarrierFrequencyRange range : ranges)
                if (frequency >= range.getMinFrequency() && frequency <= range.getMaxFrequency()) match = true;
            if (!match) throw new IllegalStateException("Your phone does not support this IR carrier frequency.");
        }
        ir.transmit(frequency, pattern);
    }
    static void validateSignal(int frequency,int[] pattern) {
        if(frequency<20000||frequency>60000)throw new IllegalArgumentException("IR frequency must be between 20 and 60 kHz.");
        if(pattern==null||pattern.length<3||pattern.length>2000)throw new IllegalArgumentException("Invalid IR pattern length.");
        long total=0;
        for(int duration:pattern){if(duration<1||duration>1000000)throw new IllegalArgumentException("Invalid IR timing.");total+=duration;}
        if(total>1900000)throw new IllegalArgumentException("IR transmission is too long (maximum 1.9 seconds).");
    }
    static int[] checkedPattern(JSONObject code) throws Exception {
        int hz = code.getInt("carrierHz");
        if (code.getDouble("carrierHz") != hz || hz < 20000 || hz > 60000) throw new IllegalArgumentException("IR frequency must be an integer between 20 and 60 kHz.");
        JSONArray array = code.getJSONArray("pattern");
        if (array.length() < 3 || array.length() > 2000) throw new IllegalArgumentException("Invalid IR pattern length.");
        int[] result = new int[array.length()];
        for (int i=0;i<result.length;i++) {
            double exact=array.getDouble(i); result[i]=array.getInt(i);
            if (exact != result[i] || result[i] < 1 || result[i] > 1000000) throw new IllegalArgumentException("IR timings must be positive integer microseconds.");
        }
        validateSignal(hz,result);
        return result;
    }
    static List<JSONObject> validateImport(JSONObject root) throws Exception {
        JSONArray array = root.getJSONArray("profiles");
        if (array.length() == 0 || array.length() > 32) throw new IllegalArgumentException("Import 1 to 32 profiles at a time.");
        List<JSONObject> validated = new ArrayList<>();
        for (int i=0;i<array.length();i++) {
            JSONObject p = new JSONObject(array.getJSONObject(i).toString());
            String brand = p.getString("brand").trim();
            String category=p.optString("category","tv");
            if(!category.equals("tv")&&!category.equals("air_conditioner"))throw new IllegalArgumentException("Category must be tv or air_conditioner.");
            if(brand.isEmpty()||brand.length()>64||brand.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("Invalid manufacturer name.");
            if(category.equals("tv")) {
                if(brand.matches("(?i)prism[ +]*")||brand.equalsIgnoreCase("PrismPlus"))brand="PRISM+";
                if(!brand.matches("(?i)Samsung|LG|Panasonic|Hisense|Astro|PRISM\\+|Daewoo"))throw new IllegalArgumentException("Choose a supported TV brand, or category air_conditioner for any AC brand.");
            }
            String model=p.getString("model").trim();
            if (model.isEmpty()||model.length()>120||model.matches("(?s).*[\\p{Cntrl}].*")) throw new IllegalArgumentException("A model name of 1 to 120 characters is required.");
            JSONObject commands = p.getJSONObject("commands");
            if (commands.length() == 0 || commands.length() > 128) throw new IllegalArgumentException("A profile must contain 1 to 128 commands.");
            java.util.Iterator<String> keys = commands.keys();
            while (keys.hasNext()) {
                String command=keys.next();
                if(!command.matches("[a-zA-Z0-9_+-]{1,80}"))throw new IllegalArgumentException("Use simple command IDs, such as power or cool_24.");
                checkedPattern(commands.getJSONObject(command));
            }
            p.put("brand",brand);p.put("model",model);p.put("category",category);
            if(category.equals("air_conditioner"))p.put("fixedState",true);
            p.put("id", "import_" + java.util.UUID.randomUUID());
            p.put("hardwareTested", false);
            p.put("source", new JSONObject().put("license", "User-supplied learned profile").put("url", "Imported locally"));
            validated.add(p);
        }
        return validated;
    }
    int importProfiles(String data) throws Exception {
        List<JSONObject> validated=validateImport(new JSONObject(data));
        JSONArray old = new JSONObject(prefs.getString("custom", "{\"profiles\":[]}")).getJSONArray("profiles");
        if (old.length() + validated.size() > 64) throw new IllegalArgumentException("Maximum of 64 imported profiles reached.");
        for (JSONObject p : validated) old.put(p);
        prefs.edit().putString("custom",new JSONObject().put("profiles",old).toString()).apply();
        profiles.addAll(validated);
        return validated.size();
    }
}
