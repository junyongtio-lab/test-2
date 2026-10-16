package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class IrSafetyTest {
    @Test public void packagedProfilesContainSafeTransmittablePatternsAndSources() throws Exception {
        JSONObject root;
        try(FileInputStream stream=new FileInputStream("src/main/assets/ir/profiles.json")) {
            root=new JSONObject(IrCatalog.read(stream));
        }
        JSONArray profiles=root.getJSONArray("profiles");
        assertTrue("The APK must contain actual profiles",profiles.length()>=4);
        Set<String> ids=new HashSet<>(),brands=new HashSet<>();
        for(int i=0;i<profiles.length();i++) {
            JSONObject p=profiles.getJSONObject(i);
            assertTrue("Profile IDs are unique",ids.add(p.getString("id")));
            brands.add(p.getString("brand"));
            assertFalse("Physical testing cannot be claimed",p.getBoolean("hardwareTested"));
            assertTrue(p.getJSONObject("source").getString("url").startsWith("https://"));
            assertFalse(p.getJSONObject("source").getString("license").isEmpty());
            JSONObject commands=p.getJSONObject("commands");
            assertTrue(commands.has("power"));
            java.util.Iterator<String> keys=commands.keys();
            while(keys.hasNext()) assertTrue(IrCatalog.checkedPattern(commands.getJSONObject(keys.next())).length>=3);
        }
        assertTrue(brands.contains("Samsung"));assertTrue(brands.contains("LG"));
        assertTrue(brands.contains("Panasonic"));assertTrue(brands.contains("Hisense"));
    }
    @Test public void invalidPatternsAreRejectedBeforeReachingHardware() throws Exception {
        for(String json:new String[]{
            "{carrierHz:38000,pattern:[9000,-1,500]}",
            "{carrierHz:38000,pattern:[9000,0,500]}",
            "{carrierHz:38000,pattern:[9000,4500,0.5]}",
            "{carrierHz:100000,pattern:[9000,4500,500]}",
            "{carrierHz:38000,pattern:[1000000,1000000,1000000]}",
            "{carrierHz:38000,pattern:[500]}"}) {
            try{IrCatalog.checkedPattern(new JSONObject(json));fail("Unsafe pattern accepted: "+json);}
            catch(IllegalArgumentException expected){/* Must reject before ConsumerIrManager.transmit. */}
        }
    }
    @Test public void untrustedImportCannotAllocateWithoutBound() throws Exception {
        try{IrCatalog.read(new ByteArrayInputStream(new byte[512*1024+1]));fail("Oversized import accepted");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("512 KB"));}
    }
}
