package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Iterator;
import static org.junit.Assert.*;

public class AstroProfilesTest {
    private JSONObject source() throws Exception {
        try (FileInputStream in = new FileInputStream("src/main/assets/astro_ultrabox.json")) {
            return new JSONObject(IrCatalog.read(in));
        }
    }
    private JSONObject profile(int index) throws Exception {
        return AstroProfiles.fromSource(source()).getJSONArray("profiles").getJSONObject(index);
    }
    private String wireBits(JSONObject profile, String command) throws Exception {
        int[] pattern = IrCatalog.checkedPattern(profile.getJSONObject("commands").getJSONObject(command));
        assertEquals(67, pattern.length); assertEquals(9000, pattern[0]); assertEquals(4500, pattern[1]);
        StringBuilder bits = new StringBuilder(); long total = 0;
        for (int duration : pattern) total += duration;
        assertEquals(67980, total);
        for (int bit = 0; bit < 32; bit++) {
            assertEquals(560, pattern[2 + bit * 2]);
            int space = pattern[3 + bit * 2]; assertTrue(space == 560 || space == 1690);
            bits.append(space == 1690 ? '1' : '0');
        }
        assertEquals(560, pattern[66]);
        return bits.toString();
    }
    @Test public void exactOwnerUploadIsPreserved() throws Exception {
        byte[] original = Files.readAllBytes(Paths.get("src/main/assets/astro_ultrabox.json"));
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(original);
        StringBuilder hex = new StringBuilder();
        for (byte value : hash) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        assertEquals("1daa99e6a4be4407ef1830c733b0522830e635683f902334b1022f582b15d747", hex.toString());
    }
    @Test public void defaultPreservesLegacyFull32BitWireOrder() throws Exception {
        JSONObject p = profile(0);
        assertEquals("astro_ultrabox_msb", p.getString("id"));
        assertEquals("00000000111111110000001011111101", wireBits(p, "power"));
        assertEquals("00000000111111111010001001011101", wireBits(p, "home"));
        assertEquals("00000000111111111000000001111111", wireBits(p, "digit_1"));
    }
    @Test public void alternateExplicitlyUsesPerByteLsbWireOrder() throws Exception {
        JSONObject p = profile(1);
        assertEquals("astro_ultrabox_lsb", p.getString("id"));
        assertEquals("00000000111111110100000010111111", wireBits(p, "power"));
        assertEquals("00000000111111110100010110111010", wireBits(p, "home"));
        assertEquals("00000000111111110000000111111110", wireBits(p, "digit_1"));
    }
    @Test public void bothProfilesExposeAllButtonsWithSafePatternsAndHonestWarnings() throws Exception {
        JSONObject original = source(); String before = original.toString();
        JSONArray profiles = AstroProfiles.fromSource(original).getJSONArray("profiles");
        assertEquals(before, original.toString()); assertEquals(2, profiles.length());
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject p = profiles.getJSONObject(i), commands = p.getJSONObject("commands");
            assertEquals("Astro", p.getString("brand")); assertEquals("tv", p.getString("category"));
            assertFalse(p.getBoolean("hardwareTested")); assertTrue(p.getString("notice").contains("same signal"));
            assertEquals(29, commands.length());
            for (int digit = 0; digit <= 9; digit++) assertTrue(commands.has("digit_" + digit));
            for (String command : new String[]{"power", "guide", "record", "red", "green", "yellow", "blue", "volume_up", "channel_down"}) assertTrue(commands.has(command));
            Iterator<String> ids = commands.keys();
            while (ids.hasNext()) {
                String id = ids.next(); JSONObject code = commands.getJSONObject(id);
                assertEquals(38000, code.getInt("carrierHz"));
                wireBits(p, id);
                String sourceName = id.startsWith("digit_") ? "NUM_" + id.substring(6) : id.equals("guide") ? "TV_GUIDE" : id.toUpperCase(java.util.Locale.ROOT);
                assertEquals(original.getJSONObject("commands").getString(sourceName), code.getString("sourceHex"));
            }
            assertArrayEquals(IrCatalog.checkedPattern(commands.getJSONObject("power")), IrCatalog.checkedPattern(commands.getJSONObject("blue")));
            assertArrayEquals(IrCatalog.checkedPattern(commands.getJSONObject("mute")), IrCatalog.checkedPattern(commands.getJSONObject("right")));
            assertFalse(commands.has("input")); assertFalse(commands.has("play_pause"));
        }
    }
    @Test public void invalidHexComplementsAndAddressAreRejected() throws Exception {
        for (String invalid : new String[]{"0x00FF02FC", "0xFF0002FD", "0x100FF02FD", "not hex"}) {
            JSONObject source = source(); source.getJSONObject("commands").put("POWER", invalid);
            try { AstroProfiles.fromSource(source); fail("Invalid source accepted: " + invalid); }
            catch (IllegalArgumentException expected) { /* Cannot send a truncated or corrupt frame. */ }
        }
    }
    @Test public void protocolCarrierAndUnsupportedButtonsCannotBeSilentlyChanged() throws Exception {
        for (String field : new String[]{"protocol", "carrier_frequency", "address", "commands"}) {
            JSONObject source = source();
            if (field.equals("commands")) source.getJSONObject("commands").put("MADE_UP", "0x00FF02FD");
            else source.put(field, field.equals("protocol") ? "NECext" : field.equals("address") ? "0xFF00" : 40000);
            try { AstroProfiles.fromSource(source); fail("Unexpected format accepted: " + field); }
            catch (IllegalArgumentException expected) { /* Asset format changes require deliberate mapping. */ }
        }
    }
}
