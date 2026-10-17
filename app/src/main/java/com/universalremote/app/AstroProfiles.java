package com.universalremote.app;

import com.universalremote.app.ir.IrEncoder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.Locale;

/** Adapts the owner's bundled NEC hex file to the same checked waveforms as other IR remotes. */
final class AstroProfiles {
    private AstroProfiles() {}

    static JSONObject fromSource(JSONObject source) throws Exception {
        if (!"Astro".equals(source.getString("manufacturer"))
                || !"IR".equals(source.getString("transmission_mode"))
                || !"NEC".equals(source.getString("protocol")))
            throw new IllegalArgumentException("The bundled Astro profile must contain NEC infrared codes.");
        if (source.getDouble("carrier_frequency") != 38000
                || !"0x00FF".equalsIgnoreCase(source.getString("address")))
            throw new IllegalArgumentException("Unexpected bundled Astro carrier or address.");
        JSONArray profiles = new JSONArray();
        // The source does not declare bit order. Classic NEC32 values use MSB-first raw frames;
        // retain a separate, explicit byte-LSB interpretation rather than silently guessing.
        for (boolean legacyMsb : new boolean[]{true, false}) {
            String interpretation = legacyMsb ? "legacy NEC32, whole frame MSB first" : "NEC bytes, each byte LSB first";
            JSONObject commands = new JSONObject();
            JSONObject originals = source.getJSONObject("commands");
            Iterator<String> keys = originals.keys();
            while (keys.hasNext()) {
                String name = keys.next();
                String hex = originals.getString(name);
                if (!hex.matches("0[xX][0-9a-fA-F]{8}")) throw new IllegalArgumentException("Invalid Astro NEC32 code: " + name);
                long raw = Long.parseLong(hex.substring(2), 16);
                int address = (int)(raw >>> 24), inverseAddress = (int)(raw >>> 16) & 255;
                int command = (int)(raw >>> 8) & 255, inverseCommand = (int)raw & 255;
                if (address != 0 || inverseAddress != 255 || (command ^ inverseCommand) != 255)
                    throw new IllegalArgumentException("Invalid Astro NEC complement bytes: " + name);
                // IrEncoder accepts canonical NEC bytes and emits them LSB first. Reversing
                // both canonical bytes therefore preserves the complete legacy MSB waveform.
                int canonicalAddress = legacyMsb ? reverseByte(address) : address;
                int canonicalCommand = legacyMsb ? reverseByte(command) : command;
                IrEncoder.Signal signal = IrEncoder.encode("NEC", canonicalAddress, canonicalCommand);
                JSONArray pattern = new JSONArray();
                for (int duration : signal.pattern) pattern.put(duration);
                JSONObject code = new JSONObject().put("label", label(name)).put("sourceHex", hex)
                    .put("carrierHz", signal.carrierHz).put("pattern", pattern);
                IrCatalog.checkedPattern(code);
                commands.put(commandId(name), code);
            }
            if (commands.length() != 29) throw new IllegalArgumentException("The bundled Astro profile must contain all 29 supplied buttons.");
            String notice = "User-supplied codes; not tested on a real Astro decoder. The supplied file gives Power and Blue the same signal, and Mute and Right the same signal. "
                + (legacyMsb ? "If the decoder does not respond, try the alternate Astro profile." : "Alternate bit order; try this if the default profile does not respond.");
            profiles.put(new JSONObject().put("id", legacyMsb ? "astro_ultrabox_msb" : "astro_ultrabox_lsb")
                .put("brand", "Astro").put("model", source.getString("device_model") + (legacyMsb ? " · default" : " · alternate"))
                .put("category", "tv").put("transport", "ir").put("hardwareTested", false)
                .put("notice", notice).put("bitOrder", interpretation)
                .put("source", new JSONObject().put("license", "NOASSERTION")
                    .put("url", "https://github.com/junyongtio-lab/test-2/blob/main/app/src/main/assets/astro_ultrabox.json")
                    .put("attribution", "Owner-supplied astro_ultrabox.json; no manufacturer verification"))
                .put("commands", commands));
        }
        return new JSONObject().put("schemaVersion", 1).put("profiles", profiles);
    }

    private static int reverseByte(int value) { return Integer.reverse(value) >>> 24; }

    private static String commandId(String name) {
        if (name.matches("NUM_[0-9]")) return "digit_" + name.substring(4);
        if (name.equals("TV_GUIDE")) return "guide";
        if (!name.matches("POWER|MUTE|HOME|BACK|UP|DOWN|LEFT|RIGHT|OK|VOLUME_UP|VOLUME_DOWN|CHANNEL_UP|CHANNEL_DOWN|RED|GREEN|YELLOW|BLUE|RECORD"))
            throw new IllegalArgumentException("Unmapped Astro button: " + name);
        return name.toLowerCase(Locale.ROOT);
    }

    private static String label(String name) {
        if (name.startsWith("NUM_")) return name.substring(4);
        if (name.equals("TV_GUIDE")) return "Guide";
        return name.replace('_', ' ');
    }
}
