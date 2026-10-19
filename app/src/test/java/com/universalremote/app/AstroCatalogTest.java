package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** The online supplement is a separate, narrowly attributed source, not an archive rewrite. */
public class AstroCatalogTest {
    private static final String BASE_ID="mi_0123456789abcdef", ASTRO_ID="mi_abcdef0123456789";
    private static final String MODEL="kk_2_3833_536";
    private JSONObject signals(String model) throws Exception {
        return new JSONObject().put("sourceModelId",model).put("commands",new JSONObject()
            .put("power",new JSONObject().put("sourceKey","power").put("label","Power")
                .put("carrierHz",38000).put("pattern",new JSONArray("[9000,4500,560,1690,560]"))));
    }
    private JSONObject metadata(boolean supplemental) throws Exception {
        String id=supplemental?ASTRO_ID:BASE_ID,model=supplemental?MODEL:"archive42";
        JSONObject source=new JSONObject().put("modelId",model).put("sourceSha256",String.format("%064d",0))
            .put("sourceFile",supplemental?"tools/mi_stb/source/keyset_"+MODEL+".json":"database_dump/42.json")
            .put("brandFile",supplemental?"tools/mi_stb/source/brands_my.json":"brands_stb.json");
        if(supplemental)source.put("brandId",3833).put("url",IrCatalog.STB_SOURCE_URL+"/app/public/get_stb_keyset");
        return new JSONObject().put("id",id).put("brand",supplemental?"Astro":"Archive brand")
            .put("model",model).put("category","set_top_box").put("hardwareTested",false).put("commandCount",1)
            .put("asset",(supplemental?"ir/mi_stb/profiles/":"ir/mi/profiles/")+id+".json")
            .put("assetSha256",IrCatalog.sha256(signals(model).toString())).put("source",source);
    }
    private JSONObject index(boolean supplemental) throws Exception {
        JSONObject source=new JSONObject().put("repository",supplemental?IrCatalog.STB_SOURCE_URL:IrCatalog.SOURCE_URL);
        if(supplemental)source.put("origin",IrCatalog.STB_SOURCE_URL).put("kind","mi-stb-public-api").put("country","MY");
        return new JSONObject().put("schemaVersion",2).put("source",source)
            .put("profiles",new JSONArray().put(metadata(supplemental)))
            .put("coverage",new JSONObject().put("profiles",1).put("commands",1).put("brands",1)
                .put("requestedBrandsAbsent",new JSONArray().put("Astro").put("PRISM+")));
    }
    private IrCatalog catalog(JSONObject base,JSONObject supplement,AtomicInteger reads) throws Exception {
        return new IrCatalog(base,supplement,path->{
            reads.incrementAndGet();String model=path.startsWith("ir/mi_stb/")?MODEL:"archive42";
            return new ByteArrayInputStream(signals(model).toString().getBytes(StandardCharsets.UTF_8));
        });
    }
    private void rejects(JSONObject base,JSONObject supplement,String reason) throws Exception {
        try {
            new IrCatalog(base,supplement,path->{throw new AssertionError("Invalid metadata opened an asset");});
            fail("Accepted invalid supplement: "+reason);
        } catch(IllegalArgumentException|org.json.JSONException expected) { /* No partial catalogue is returned. */ }
    }
    @Test public void mergeIsLazyAndKeepsArchiveMetadataAndCoverageUnchanged() throws Exception {
        JSONObject base=index(false),supplement=index(true);String original=base.toString();
        supplement.getJSONObject("coverage").put("profiles",99999).put("commands",99999);
        AtomicInteger reads=new AtomicInteger();IrCatalog catalog=catalog(base,supplement,reads);
        assertEquals(original,base.toString());assertEquals(0,reads.get());assertEquals(2,catalog.profileCount());
        assertEquals(1,catalog.forBrand("Astro","set_top_box").size());
        assertEquals(2,catalog.coverage().getInt("profiles"));assertEquals(2,catalog.coverage().getInt("commands"));
        assertEquals(2,catalog.coverage().getInt("brands"));
        JSONObject category=catalog.coverage().getJSONObject("categories").getJSONObject("set_top_box");
        assertEquals(2,category.getInt("profiles"));assertEquals(2,category.getInt("commands"));assertEquals(2,category.getInt("brands"));
        assertEquals("[\"PRISM+\"]",catalog.coverage().getJSONArray("requestedBrandsAbsent").toString());
        assertEquals("archive42",catalog.loadProfile(BASE_ID).getJSONObject("source").getString("modelId"));
        JSONObject astro=catalog.loadProfile(ASTRO_ID);assertEquals(MODEL,astro.getString("model"));
        assertTrue(catalog.supports(ASTRO_ID,"power"));assertFalse(astro.getBoolean("hardwareTested"));assertEquals(2,reads.get());
    }
    @Test public void existingArchiveOnlyConstructorDoesNotReadOrInventSupplement() throws Exception {
        IrCatalog catalog=new IrCatalog(index(false),path->{throw new AssertionError("Metadata eagerly loaded signals");});
        assertEquals(1,catalog.profileCount());assertTrue(catalog.forBrand("Astro","set_top_box").isEmpty());
        assertEquals(1,catalog.coverage().getInt("profiles"));
        assertEquals("Astro",catalog.coverage().getJSONArray("requestedBrandsAbsent").getString(0));
    }
    @Test public void onlineSourceMustMatchExactOriginCountryAndSourceKind() throws Exception {
        for(String mutation:new String[]{"repository","origin","kind","country","schema","empty"}) {
            JSONObject root=index(true),source=root.getJSONObject("source");
            if(mutation.equals("repository"))source.put("repository",IrCatalog.STB_SOURCE_URL+".invalid");
            if(mutation.equals("origin"))source.put("origin",IrCatalog.STB_SOURCE_URL+"/unrelated");
            if(mutation.equals("kind"))source.put("kind","unverified-import");
            if(mutation.equals("country"))source.put("country","ID");
            if(mutation.equals("schema"))root.put("schemaVersion",2.1);
            if(mutation.equals("empty"))root.put("profiles",new JSONArray());
            rejects(index(false),root,mutation);
        }
    }
    @Test public void supplementCannotEscapeAssetsRelabelModelsOrOverrideArchiveIds() throws Exception {
        for(String mutation:new String[]{"asset","duplicate","brand","category","model","modelId","brandId","url","sourceFile","brandFile","digest","tested"}) {
            JSONObject root=index(true),profile=root.getJSONArray("profiles").getJSONObject(0),source=profile.getJSONObject("source");
            if(mutation.equals("asset"))profile.put("asset","ir/mi/profiles/"+ASTRO_ID+".json");
            if(mutation.equals("duplicate"))profile.put("id",BASE_ID).put("asset","ir/mi_stb/profiles/"+BASE_ID+".json");
            if(mutation.equals("brand"))profile.put("brand","NJOI");
            if(mutation.equals("category"))profile.put("category","tv");
            if(mutation.equals("model"))profile.put("model","Ultra Box verified");
            if(mutation.equals("modelId"))source.put("modelId","kk_2_9999_536");
            if(mutation.equals("brandId"))source.put("brandId",3833.5);
            if(mutation.equals("url"))source.put("url",IrCatalog.STB_SOURCE_URL+"/app/public/other");
            if(mutation.equals("sourceFile"))source.put("sourceFile","../private/astro.json");
            if(mutation.equals("brandFile"))source.put("brandFile","other-brands.json");
            if(mutation.equals("digest"))source.put("sourceSha256","unknown");
            if(mutation.equals("tested"))profile.put("hardwareTested",true);
            rejects(index(false),root,mutation);
        }
    }
    @Test public void supplementCannotRepeatASourceModelUnderDifferentIds() throws Exception {
        JSONObject root=index(true),other=new JSONObject(root.getJSONArray("profiles").getJSONObject(0).toString());
        other.put("id","mi_7777777777777777").put("asset","ir/mi_stb/profiles/mi_7777777777777777.json");
        root.getJSONArray("profiles").put(other);rejects(index(false),root,"repeated source model");
    }
    @Test public void supplementalSignalsStillRequireMatchingBytesAndSourceModel() throws Exception {
        for(boolean wrongDigest:new boolean[]{true,false}) {
            JSONObject supplement=index(true),data=signals("kk_2_3833_1819");
            if(!wrongDigest)supplement.getJSONArray("profiles").getJSONObject(0).put("assetSha256",IrCatalog.sha256(data.toString()));
            IrCatalog catalog=new IrCatalog(index(false),supplement,path->new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.UTF_8)));
            try{catalog.loadProfile(ASTRO_ID);fail("Accepted changed Astro signal data");}
            catch(IllegalArgumentException expected){assertFalse(catalog.isLoaded(ASTRO_ID));}
        }
    }
    private JSONObject bundledIndex(String path) throws Exception {
        try(FileInputStream in=new FileInputStream("src/main/assets/"+path)) {
            return new JSONObject(IrCatalog.readBounded(in,16*1024*1024));
        }
    }
    @Test public void allPackagedAstroSignalsLoadAlongsideTheUnchangedArchive() throws Exception {
        JSONObject base=bundledIndex("ir/mi/index.json"),supplement=bundledIndex("ir/mi_stb/index.json");
        IrCatalog catalog=new IrCatalog(base,supplement,path->new FileInputStream("src/main/assets/"+path));
        assertEquals(3706,base.getJSONArray("profiles").length());assertEquals(3711,catalog.profileCount());
        assertEquals(141165,catalog.coverage().getInt("commands"));
        assertEquals(5,catalog.forBrand("Astro","set_top_box").size());
        java.util.Set<String> expected=new java.util.HashSet<>(java.util.Arrays.asList("kk_2_3833_536","kk_2_3833_1819","kk_2_3833_350","kk_2_3833_211","kk_2_3833_2160"));
        int commands=0;
        for(JSONObject metadata:catalog.forBrand("Astro","set_top_box")) {
            String id=metadata.getString("id"),model=metadata.getJSONObject("source").getString("modelId");
            assertTrue("Unexpected source keyset: "+model,expected.remove(model));
            assertFalse(catalog.isLoaded(id));JSONObject loaded=catalog.loadProfile(id);
            assertFalse(loaded.getBoolean("hardwareTested"));assertEquals(model,loaded.getString("model"));
            JSONObject signals=loaded.getJSONObject("commands");assertEquals(metadata.getInt("commandCount"),signals.length());
            java.util.Iterator<String> keys=signals.keys();
            while(keys.hasNext()) {
                String key=keys.next();JSONObject signal=signals.getJSONObject(key);
                assertEquals(key,signal.getString("sourceKey"));IrCatalog.checkedPattern(signal);commands++;
            }
        }
        assertTrue(expected.isEmpty());assertEquals(216,commands);
        JSONObject first=base.getJSONArray("profiles").getJSONObject(0);
        assertEquals(first.getInt("commandCount"),catalog.loadProfile(first.getString("id")).getJSONObject("commands").length());
        assertNull(catalog.find("astro_ultrabox_msb"));assertTrue(catalog.forBrand("PRISM+","tv").isEmpty());
        assertFalse(catalog.coverage().getJSONArray("requestedBrandsAbsent").toString().contains("Astro"));
    }
}
