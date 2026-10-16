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
    @Test public void everyNewCatalogueEntryIsSafeAndHasHonestProvenance() throws Exception {
        Set<String> ids=new HashSet<>(),tvBrands=new HashSet<>(),acBrands=new HashSet<>();
        int count=0;
        for(String asset:new String[]{"profiles.json","tv_extra/profiles.json","ac_captures/profiles.json"}) {
            JSONObject root;
            try(FileInputStream stream=new FileInputStream("src/main/assets/ir/"+asset)) {
                root=new JSONObject(IrCatalog.readBounded(stream,8*1024*1024));
            }
            JSONArray profiles=root.getJSONArray("profiles");
            for(int i=0;i<profiles.length();i++) {
                JSONObject profile=profiles.getJSONObject(i);
                assertTrue("Cross-catalogue IDs must be unique",ids.add(profile.getString("id")));
                assertFalse(profile.getBoolean("hardwareTested"));
                assertEquals("Legacy capture licenses must not be fabricated","NOASSERTION",profile.getJSONObject("source").getString("license"));
                assertTrue(profile.getJSONObject("source").getString("url").startsWith("https://"));
                if(profile.getString("category").equals("air_conditioner")) {
                    assertTrue("Captured AC commands are not an arbitrary-temperature encoder",profile.getBoolean("fixedState"));
                    acBrands.add(profile.getString("brand"));
                } else {assertEquals("tv",profile.getString("category"));tvBrands.add(profile.getString("brand"));}
                JSONObject commands=profile.getJSONObject("commands");assertTrue(commands.length()>0);
                java.util.Iterator<String> keys=commands.keys();
                while(keys.hasNext()){IrCatalog.checkedPattern(commands.getJSONObject(keys.next()));count++;}
            }
        }
        assertTrue(tvBrands.contains("PRISM+"));assertTrue(tvBrands.contains("Daewoo"));
        assertTrue(acBrands.contains("Panasonic"));assertTrue(acBrands.contains("Daikin"));
        assertTrue(acBrands.size()>=96);assertTrue(count>=2039);
    }
    private JSONObject imported(String brand,String category) throws Exception {
        return new JSONObject().put("profiles",new JSONArray().put(new JSONObject()
            .put("brand",brand).put("category",category).put("model","Owner's captured remote")
            .put("hardwareTested",true).put("commands",new JSONObject().put("cool_24",
                new JSONObject().put("carrierHz",38000).put("pattern",new JSONArray("[9000,4500,560]"))))));
    }
    @Test public void anyAcManufacturerCanImportWithoutPretendingItsTvIsSupported() throws Exception {
        JSONObject input=imported("New AC manufacturer","air_conditioner");
        JSONObject saved=IrCatalog.validateImport(input).get(0);
        assertEquals("New AC manufacturer",saved.getString("brand"));assertTrue(saved.getBoolean("fixedState"));
        assertFalse(saved.getBoolean("hardwareTested"));assertTrue(input.getJSONArray("profiles").getJSONObject(0).getBoolean("hardwareTested"));
        try{IrCatalog.validateImport(imported("New AC manufacturer","tv"));fail("Unknown TV brand accepted");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("TV brand"));}
    }
    @Test public void expandedImportsRejectWrongCategoriesAndUnsafeTimings() throws Exception {
        for(String category:new String[]{"fan","ac"}) {
            try{IrCatalog.validateImport(imported("Daewoo",category));fail("Unknown category accepted");}
            catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("Category"));}
        }
        JSONObject data=imported("PRISM+","tv");
        data.getJSONArray("profiles").getJSONObject(0).getJSONObject("commands").getJSONObject("cool_24").put("pattern",new JSONArray("[9000,-1,560]"));
        try{IrCatalog.validateImport(data);fail("Unsafe import accepted");}catch(IllegalArgumentException expected){/* Invalid input never reaches storage or hardware. */}
    }
    @Test public void prismNamesCanonicalizeAndOldCategorylessImportsStillWork() throws Exception {
        assertEquals("PRISM+",IrCatalog.validateImport(imported("PrismPlus","tv")).get(0).getString("brand"));
        JSONObject old=imported("Samsung","tv");old.getJSONArray("profiles").getJSONObject(0).remove("category");
        assertEquals("tv",IrCatalog.validateImport(old).get(0).getString("category"));
    }
}
