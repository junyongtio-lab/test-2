package com.universalremote.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local device metadata; pairing credentials encrypted by a nonexportable Android Keystore key. */
final class DeviceStore {
    private final SharedPreferences prefs;
    DeviceStore(Context context) { prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE); }
    List<JSONObject> load() {
        List<JSONObject> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("profiles", "[]"));
            for (int i = 0; i < array.length(); i++) result.add(array.getJSONObject(i));
        } catch (Exception ignored) { /* Never discard the stored value on a read failure. */ }
        return result;
    }
    void save(List<JSONObject> devices) {
        JSONArray array = new JSONArray();
        for (JSONObject device : devices) array.put(device);
        prefs.edit().putString("profiles", array.toString()).apply();
    }
    /** Run only after the replacement Mi index has loaded successfully. */
    int removeUnavailableIrDevices(IrCatalog catalogue) {
        try {
            // Parse the whole value before writing: malformed data must not lose pairings.
            JSONArray saved = new JSONArray(prefs.getString("profiles", "[]"));
            MiCatalogueMigration.Result result = MiCatalogueMigration.removeUnavailable(saved,
                id -> catalogue.find(id) != null);
            if(result.removedCount == 0) return 0;
            SharedPreferences.Editor update = prefs.edit().putString("profiles", result.retained.toString());
            for(String id : result.removedIds) update.remove("credential." + id);
            update.apply();
            return result.removedCount;
        } catch(Exception ignored) { return 0; /* Preserve the stored value on a parse failure. */ }
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias("palm.remote.credentials")) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("palm.remote.credentials",
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry)store.getEntry("palm.remote.credentials", null)).getSecretKey();
    }
    void putCredential(String id, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        prefs.edit().putString("credential." + id,
            Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply();
    }
    String credential(String id) throws Exception {
        String saved = prefs.getString("credential." + id, "");
        if (saved.isEmpty()) return "";
        String[] parts = saved.split(":", 2);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    void delete(String id) { prefs.edit().remove("credential." + id).apply(); }
}
