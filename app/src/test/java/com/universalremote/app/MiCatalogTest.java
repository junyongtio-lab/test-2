package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class MiCatalogTest {
    private static final String ID = "mi_0123456789abcdef";
    private JSONObject metadata(String id, String category) throws Exception {
        return new JSONObject().put("id",id).put("brand","Example brand").put("model","Mi keyset 42")
            .put("category",category).put("commandCount",1).put("hardwareTested",false)
            .put("assetSha256",IrCatalog.sha256(signals().toString()))
            .put("asset","ir/mi/profiles/"+id+".json").put("source",new JSONObject()
                .put("sourceFile","database_dump/ir/STB/42.json").put("brandFile","brands_stb.json")
                .put("modelId","42").put("sourceSha256",String.format("%064d",0)));
    }
    private JSONObject index(JSONObject... entries) throws Exception {
        JSONArray profiles = new JSONArray();for(JSONObject entry:entries)profiles.put(entry);
        return new JSONObject().put("schemaVersion",2).put("source",new JSONObject().put("repository",IrCatalog.SOURCE_URL))
            .put("profiles",profiles).put("coverage",new JSONObject().put("description","Source coverage"));
    }
    private JSONObject signals() throws Exception {
        return new JSONObject().put("sourceModelId","42").put("commands",new JSONObject().put("Power:source",new JSONObject()
            .put("label","Original power label").put("sourceKey","Power:source").put("carrierHz",38000)
            .put("pattern",new JSONArray("[9000,4500,560,1690,560]"))));
    }
    private IrCatalog catalogue(JSONObject index, JSONObject signals, AtomicInteger reads) throws Exception {
        byte[] data=signals.toString().getBytes(StandardCharsets.UTF_8);
        return new IrCatalog(index,asset->{reads.incrementAndGet();return new ByteArrayInputStream(data);});
    }
    @Test public void metadataLoadsWithoutReadingSignalsAndSourceKeysRemainUnchanged() throws Exception {
        AtomicInteger reads=new AtomicInteger();IrCatalog catalog=catalogue(index(metadata(ID,"set_top_box")),signals(),reads);
        assertEquals(1,catalog.profileCount());assertEquals(0,reads.get());assertFalse(catalog.isLoaded(ID));
        assertFalse(catalog.supports(ID,"Power:source"));assertFalse(catalog.find(ID).has("commands"));
        assertTrue(catalog.brands("set_top_box").contains("Example brand"));assertTrue(catalog.forBrand("Example brand").isEmpty());
        JSONObject loaded=catalog.loadProfile(ID);
        assertTrue(catalog.isLoaded(ID));assertTrue(catalog.supports(ID,"Power:source"));assertFalse(catalog.supports(ID,"power"));
        assertArrayEquals(new int[]{9000,4500,560,1690,560},IrCatalog.checkedPattern(loaded.getJSONObject("commands").getJSONObject("Power:source")));
        assertEquals("Original power label",loaded.getJSONObject("commands").getJSONObject("Power:source").getString("label"));
        assertSame(loaded,catalog.find(ID));assertSame(loaded,catalog.loadProfile(ID));assertEquals(1,reads.get());
    }
    @Test public void signalCacheIsBoundedAndEvictsTheLeastRecentlyUsedProfile() throws Exception {
        JSONObject[] entries=new JSONObject[9];for(int i=0;i<entries.length;i++)entries[i]=metadata(String.format("mi_%016x",i),"tv");
        AtomicInteger reads=new AtomicInteger();IrCatalog catalog=catalogue(index(entries),signals(),reads);
        for(int i=0;i<8;i++)catalog.loadProfile(entries[i].getString("id"));
        catalog.loadProfile(entries[0].getString("id"));catalog.loadProfile(entries[8].getString("id"));
        assertTrue(catalog.isLoaded(entries[0].getString("id")));assertFalse(catalog.isLoaded(entries[1].getString("id")));
        assertFalse(catalog.find(entries[1].getString("id")).has("commands"));assertEquals(9,reads.get());
        catalog.loadProfile(entries[1].getString("id"));assertEquals(10,reads.get());
    }
    @Test public void invalidIndexCannotEscapeAssetsOrClaimAnUnrelatedSource() throws Exception {
        for(String mutation:new String[]{"asset","id","category","source","digest","duplicate","count","tested"}) {
            JSONObject p=metadata(ID,"tv"),root=index(p);
            if(mutation.equals("asset"))p.put("asset","../old/profiles.json");
            if(mutation.equals("id"))p.put("id","astro_ultrabox_msb");
            if(mutation.equals("category"))p.put("category","other");
            if(mutation.equals("source"))root.getJSONObject("source").put("repository",IrCatalog.SOURCE_URL+"-other");
            if(mutation.equals("digest"))p.getJSONObject("source").put("sourceSha256","missing");
            if(mutation.equals("duplicate"))root.getJSONArray("profiles").put(p);
            if(mutation.equals("count"))p.put("commandCount",1.5);
            if(mutation.equals("tested"))p.put("hardwareTested",true);
            try{catalogue(root,signals(),new AtomicInteger());fail("Accepted invalid index: "+mutation);}
            catch(IllegalArgumentException expected){/* Rejected before a profile asset is opened. */}
        }
    }
    @Test public void mismatchedOrUnsafeSignalsNeverEnterTheCache() throws Exception {
        for(String mutation:new String[]{"model","count","key","frequency","timing"}) {
            JSONObject data=signals(),signal=data.getJSONObject("commands").getJSONObject("Power:source");
            if(mutation.equals("model"))data.put("sourceModelId","43");
            if(mutation.equals("count"))data.getJSONObject("commands").put("extra",signal);
            if(mutation.equals("key"))signal.put("sourceKey","rewritten");
            if(mutation.equals("frequency"))signal.put("carrierHz",38000.5);
            if(mutation.equals("timing"))signal.put("pattern",new JSONArray("[9000,4500,-560]"));
            JSONObject metadata=metadata(ID,"air_conditioner").put("assetSha256",IrCatalog.sha256(data.toString()));
            IrCatalog catalog=catalogue(index(metadata),data,new AtomicInteger());
            try{catalog.loadProfile(ID);fail("Accepted unsafe signal: "+mutation);}
            catch(IllegalArgumentException expected){assertFalse(catalog.isLoaded(ID));}
        }
    }
    @Test public void changedAssetBytesAreRejectedBeforeAnyCommandsAreCached() throws Exception {
        JSONObject data=signals();data.getJSONObject("commands").getJSONObject("Power:source").put("pattern",new JSONArray("[9000,4500,561]"));
        IrCatalog catalog=catalogue(index(metadata(ID,"tv")),data,new AtomicInteger());
        try{catalog.loadProfile(ID);fail("Signal changed without updating its digest");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("digest"));assertFalse(catalog.isLoaded(ID));}
    }
    @Test public void profileReadIsBoundedBeforeJsonAllocation() throws Exception {
        try{IrCatalog.read(new ByteArrayInputStream(new byte[IrCatalog.PROFILE_LIMIT+1]));fail("Oversized profile accepted");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("512 KB"));}
    }
    @Test public void invalidPatternsAreRejectedBeforeReachingHardware() throws Exception {
        for(String json:new String[]{"{carrierHz:38000,pattern:[9000,-1,500]}","{carrierHz:38000,pattern:[9000,0,500]}",
            "{carrierHz:38000,pattern:[9000,4500,0.5]}","{carrierHz:100000,pattern:[9000,4500,500]}",
            "{carrierHz:38000,pattern:[1000000,1000000,1000000]}","{carrierHz:38000,pattern:[500]}"}) {
            try{IrCatalog.checkedPattern(new JSONObject(json));fail("Unsafe pattern accepted: "+json);}
            catch(IllegalArgumentException expected){/* No signal reaches ConsumerIrManager. */}
        }
    }
    @Test public void bundledIndexLoadsOnlyMiAssetsAcrossEveryPublishedCategory() throws Exception {
        JSONObject root;try(FileInputStream in=new FileInputStream("src/main/assets/ir/mi/index.json")) {
            root=new JSONObject(IrCatalog.readBounded(in,16*1024*1024));
        }
        IrCatalog catalog=new IrCatalog(root,path->new FileInputStream("src/main/assets/"+path));
        assertTrue(catalog.profileCount()>0);
        for(String category:new String[]{"tv","set_top_box","air_conditioner","fan"}) {
            assertFalse("Category unavailable: "+category,catalog.brands(category).isEmpty());
            String brand=catalog.brands(category).iterator().next();JSONObject entry=catalog.forBrand(brand,category).get(0);
            assertFalse(catalog.isLoaded(entry.getString("id")));JSONObject loaded=catalog.loadProfile(entry.getString("id"));
            assertFalse(loaded.optBoolean("hardwareTested"));assertEquals(category,loaded.getString("category"));
            assertEquals(entry.getInt("commandCount"),loaded.getJSONObject("commands").length());
        }
        assertNull(catalog.find("astro_ultrabox_msb"));assertNull(catalog.find("samsung_bn59"));
    }
    @Test public void everyBundledProfilePassesTheProductionLoaderAndRetainsAllSourceCommands() throws Exception {
        JSONObject root;try(FileInputStream in=new FileInputStream("src/main/assets/ir/mi/index.json")) {
            root=new JSONObject(IrCatalog.readBounded(in,16*1024*1024));
        }
        IrCatalog catalog=new IrCatalog(root,path->new FileInputStream("src/main/assets/"+path));
        JSONArray profiles=root.getJSONArray("profiles");int commands=0;
        for(int i=0;i<profiles.length();i++) {
            JSONObject metadata=profiles.getJSONObject(i);String id=metadata.getString("id");
            JSONObject loaded=catalog.loadProfile(id);JSONObject signals=loaded.getJSONObject("commands");
            assertEquals("Command count changed: "+id,metadata.getInt("commandCount"),signals.length());
            java.util.Iterator<String> keys=signals.keys();
            while(keys.hasNext()) {
                String key=keys.next();assertEquals(key,signals.getJSONObject(key).getString("sourceKey"));
                assertTrue(catalog.supports(id,key));commands++;
            }
            if(i>=8)assertFalse("Full-catalogue validation must retain at most eight profiles",catalog.isLoaded(profiles.getJSONObject(i-8).getString("id")));
        }
        assertEquals("Bundled Mi remote keysets unexpectedly changed",3706,catalog.profileCount());
        assertEquals("Bundled source commands unexpectedly changed",140949,commands);
    }
}
