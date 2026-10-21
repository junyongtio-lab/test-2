package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Separate APK provenance and source ordering must not rewrite existing remotes. */
public class ApkCatalogTest {
    private static final String A="mi_1111111111111111", B="mi_2222222222222222", FAN="mi_3333333333333333";
    private static final String APK_TV="mi_4444444444444444", APK_AC="mi_5555555555555555";
    private static final String BRAND_FILE="database_dump/1_TV/Daewoo_195.json";
    private static final String DIGEST="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private JSONObject signals(String model) throws Exception {
        return new JSONObject().put("sourceModelId",model).put("commands",new JSONObject().put("power",new JSONObject()
            .put("sourceKey","power").put("label","Power").put("carrierHz",38000)
            .put("pattern",new JSONArray("[9000,4500,560,1690,560]"))));
    }
    private JSONObject metadata(String id,String model,String brand,String category,boolean apk) throws Exception {
        JSONObject source=new JSONObject().put("modelId",model).put("sourceSha256",DIGEST).put("brandSha256",DIGEST);
        if(apk) source.put("format","microsecond-json-array").put("sourceTable","kk_remote_ircode").put("brandId",category.equals("tv")?"195":"137")
            .put("sourceDeviceId",category.equals("tv")?1:3).put("sourceFile","irlocaldata.db/kk_remote_ircode/"+model)
            .put("brandFile","irlocaldata.db/brand/"+(category.equals("tv")?"195":"137"));
        else source.put("sourceFile","database_dump/"+model+".json").put("brandFile",BRAND_FILE);
        return new JSONObject().put("id",id).put("model",model).put("brand",brand).put("category",category)
            .put("hardwareTested",false).put("commandCount",1).put("source",source)
            .put("asset",(apk?"ir/mi_apk/profiles/":"ir/mi/profiles/")+id+".json")
            .put("assetSha256",IrCatalog.sha256(signals(model).toString()));
    }
    private JSONObject base() throws Exception {
        return new JSONObject().put("schemaVersion",2).put("source",new JSONObject().put("repository",IrCatalog.SOURCE_URL))
            .put("coverage",new JSONObject().put("profiles",3).put("commands",3))
            .put("profiles",new JSONArray().put(metadata(A,"tv_a","Daewoo","tv",false))
                .put(metadata(B,"tv_b","Daewoo","tv",false)).put(metadata(FAN,"fan","Fan brand","fan",false)));
    }
    private JSONObject apk() throws Exception {
        return new JSONObject().put("schemaVersion",2).put("source",new JSONObject()
            .put("repository",IrCatalog.APK_SOURCE_URL).put("kind","mi-apk-bundled-database")
            .put("apkSha256",IrCatalog.APK_SHA256).put("databaseSha256",IrCatalog.APK_DATABASE_SHA256)
            .put("archiveSha256",IrCatalog.APK_ARCHIVE_SHA256).put("frequencyUnit","Hz").put("timingUnit","microseconds")
            .put("versionName","7.2.4M").put("versionCode",6500))
            .put("coverage",new JSONObject().put("profiles",99999).put("commands",99999))
            .put("profiles",new JSONArray().put(metadata(APK_TV,"tv42","Daewoo","tv",true))
                .put(metadata(APK_AC,"ac42","Panasonic","air_conditioner",true)));
    }
    private JSONObject order() throws Exception {
        return new JSONObject().put("schemaVersion",1).put("source",new JSONObject()
            .put("repository",IrCatalog.SOURCE_URL).put("release","v2.1").put("country","MY")
            .put("archiveSha256",IrCatalog.ARCHIVE_SHA256).put("indexSha256",IrCatalog.ARCHIVE_INDEX_SHA256))
            .put("groups",new JSONArray().put(new JSONObject().put("brand","Daewoo").put("category","tv")
                .put("sourceBrandFile",BRAND_FILE).put("sourceBrandSha256",DIGEST).put("profiles",new JSONArray()
                    .put(new JSONObject().put("id",B).put("modelId","tv_b"))
                    .put(new JSONObject().put("id",A).put("modelId","tv_a")))));
    }
    private IrCatalog catalog(JSONObject base,JSONObject apk,JSONObject order,AtomicInteger reads) throws Exception {
        List<JSONObject> entries=new ArrayList<>();
        for(JSONObject index:Arrays.asList(base,apk)) if(index!=null) {
            JSONArray rows=index.getJSONArray("profiles");for(int i=0;i<rows.length();i++)entries.add(rows.getJSONObject(i));
        }
        return new IrCatalog(base,null,apk,order,path->{
            reads.incrementAndGet();for(JSONObject entry:entries)if(entry.getString("asset").equals(path))
                return new ByteArrayInputStream(signals(entry.getJSONObject("source").getString("modelId")).toString().getBytes(StandardCharsets.UTF_8));
            throw new AssertionError("Unexpected signal path "+path);
        });
    }
    private List<String> ids(List<JSONObject> profiles) {
        List<String> ids=new ArrayList<>();for(JSONObject p:profiles)ids.add(p.optString("id"));return ids;
    }
    private void rejects(JSONObject base,JSONObject apk,JSONObject order,String mutation) throws Exception {
        try {
            new IrCatalog(base,null,apk,order,path->{throw new AssertionError("Invalid index opened signal assets");});
            fail("Accepted invalid catalogue metadata: "+mutation);
        }catch(IllegalArgumentException|org.json.JSONException expected){/* Invalid metadata never returns a catalogue. */}
    }
    @Test public void separateApkRecordsMergeLazilyAndSourceOrderNeverDropsCandidates() throws Exception {
        JSONObject base=base(),apk=apk(),order=order();String originalBase=base.toString(),originalOrder=order.toString();
        AtomicInteger reads=new AtomicInteger();IrCatalog catalog=catalog(base,apk,order,reads);
        assertEquals(0,reads.get());assertEquals(originalBase,base.toString());assertEquals(originalOrder,order.toString());
        assertEquals(5,catalog.profileCount());assertEquals(5,catalog.coverage().getInt("commands"));
        assertEquals(3,catalog.coverage().getJSONObject("categories").getJSONObject("tv").getInt("profiles"));
        assertEquals(Arrays.asList(B,A,APK_TV),ids(catalog.forBrand("Daewoo","tv")));
        assertEquals(Arrays.asList(APK_AC),ids(catalog.forBrand("Panasonic","air_conditioner")));
        assertEquals(Arrays.asList(FAN),ids(catalog.forBrand("Fan brand","fan")));
        JSONObject loaded=catalog.loadProfile(APK_TV);assertEquals("tv42",loaded.getString("model"));
        assertTrue(catalog.supports(APK_TV,"power"));assertEquals(1,reads.get());
    }
    @Test public void apkSourceMustIdentifyTheExactUploadedReleaseAndDatabase() throws Exception {
        for(String mutation:new String[]{"repository","kind","apkSha256","archiveSha256","databaseSha256","timingUnit","frequencyUnit","versionName","versionCode","schema","empty"}) {
            JSONObject root=apk(),source=root.getJSONObject("source");
            if(mutation.equals("repository"))source.put(mutation,IrCatalog.APK_SOURCE_URL+"-other");
            if(mutation.equals("kind"))source.put(mutation,"unverified-import");
            if(mutation.equals("apkSha256")||mutation.equals("archiveSha256")||mutation.equals("databaseSha256"))source.put(mutation,DIGEST);
            if(mutation.equals("timingUnit"))source.put(mutation,"carrier cycles");
            if(mutation.equals("frequencyUnit"))source.put(mutation,"kHz");
            if(mutation.equals("versionName"))source.put(mutation,"7.4.2M");
            if(mutation.equals("versionCode"))source.put(mutation,"6500");
            if(mutation.equals("schema"))root.put("schemaVersion",2.1);
            if(mutation.equals("empty"))root.put("profiles",new JSONArray());
            rejects(base(),root,order(),mutation);
        }
    }
    @Test public void apkRowsCannotEscapeAssetsChangeCategoriesOrCollideWithExistingRemotes() throws Exception {
        for(String mutation:new String[]{"asset","duplicate","table","model","row","device","brand","brandFile","sourceFile","brandDigest","sourceDigest","format","encoding","tested"}) {
            JSONObject root=apk(),p=root.getJSONArray("profiles").getJSONObject(0),s=p.getJSONObject("source");
            if(mutation.equals("asset"))p.put("asset","ir/mi/profiles/"+APK_TV+".json");
            if(mutation.equals("duplicate"))p.put("id",A).put("asset","ir/mi_apk/profiles/"+A+".json");
            if(mutation.equals("table"))s.put("sourceTable","kk_remote_ircode_src");
            if(mutation.equals("model"))p.put("model","verified retail model");
            if(mutation.equals("row"))s.put("modelId","../tv42");
            if(mutation.equals("device"))s.put("sourceDeviceId",3);
            if(mutation.equals("brand"))s.put("brandId",195);
            if(mutation.equals("brandFile"))s.put("brandFile","irlocaldata.db/brand/137");
            if(mutation.equals("sourceFile"))s.put("sourceFile","irlocaldata.db/other/tv42");
            if(mutation.equals("brandDigest"))s.put("brandSha256","unknown");
            if(mutation.equals("sourceDigest"))s.put("sourceSha256","unknown");
            if(mutation.equals("format"))s.put("format","carrier-cycle-array");
            if(mutation.equals("encoding"))s.put("format","aes-gzip-microsecond-json-array");
            if(mutation.equals("tested"))p.put("hardwareTested",true);
            rejects(base(),root,order(),mutation);
        }
        JSONObject root=apk(),copy=new JSONObject(root.getJSONArray("profiles").getJSONObject(0).toString());
        copy.put("id","mi_6666666666666666").put("asset","ir/mi_apk/profiles/mi_6666666666666666.json");
        root.getJSONArray("profiles").put(copy);rejects(base(),root,order(),"duplicate native row");
    }
    @Test public void matchingOrderRequiresItsPinnedArchiveAndIndex() throws Exception {
        for(String mutation:new String[]{"repository","release","archiveSha256","indexSha256","country","schema","empty"}) {
            JSONObject root=order(),source=root.getJSONObject("source");
            if(mutation.equals("repository"))source.put(mutation,IrCatalog.SOURCE_URL+"-other");
            if(mutation.equals("release"))source.put(mutation,"v3");
            if(mutation.equals("archiveSha256")||mutation.equals("indexSha256"))source.put(mutation,DIGEST);
            if(mutation.equals("country"))source.put(mutation,"ID");
            if(mutation.equals("schema"))root.put("schemaVersion",1.1);
            if(mutation.equals("empty"))root.put("groups",new JSONArray());
            rejects(base(),apk(),root,mutation);
        }
    }
    @Test public void matchingCannotRelabelOmitDuplicateOrRankSupplementalProfiles() throws Exception {
        for(String mutation:new String[]{"brand","category","file","digest","model","unknown","supplement","duplicate","omitted","group"}) {
            JSONObject root=order(),group=root.getJSONArray("groups").getJSONObject(0);
            JSONArray entries=group.getJSONArray("profiles");JSONObject first=entries.getJSONObject(0);
            if(mutation.equals("brand"))group.put("brand","Another brand");
            if(mutation.equals("category"))group.put("category","air_conditioner");
            if(mutation.equals("file"))group.put("sourceBrandFile","other.json");
            if(mutation.equals("digest"))group.put("sourceBrandSha256",String.format("%064d",0));
            if(mutation.equals("model"))first.put("modelId","wrong-model");
            if(mutation.equals("unknown"))first.put("id","mi_7777777777777777");
            if(mutation.equals("supplement"))first.put("id",APK_TV).put("modelId","tv42");
            if(mutation.equals("duplicate"))entries.put(1,new JSONObject(first.toString()));
            if(mutation.equals("omitted"))group.put("profiles",new JSONArray().put(first));
            if(mutation.equals("group"))root.getJSONArray("groups").put(new JSONObject(group.toString()));
            rejects(base(),apk(),root,mutation);
        }
    }
    @Test public void nativeSignalBytesStillRequireTheirExactDigestAndModelBeforeCaching() throws Exception {
        for(boolean mismatchDigest:new boolean[]{true,false}) {
            JSONObject apk=apk(),wrong=signals("other_native_row");
            if(!mismatchDigest)apk.getJSONArray("profiles").getJSONObject(0).put("assetSha256",IrCatalog.sha256(wrong.toString()));
            IrCatalog catalog=new IrCatalog(base(),null,apk,order(),path->new ByteArrayInputStream(wrong.toString().getBytes(StandardCharsets.UTF_8)));
            try{catalog.loadProfile(APK_TV);fail("Accepted changed native signal data");}
            catch(IllegalArgumentException expected){assertFalse(catalog.isLoaded(APK_TV));}
        }
    }
    private JSONObject index(String path) throws Exception {
        try(FileInputStream in=new FileInputStream("src/main/assets/"+path)) {
            return new JSONObject(IrCatalog.readBounded(in,16*1024*1024));
        }
    }
    @Test public void packagedApkSignalsAndDaewooMatchingOrderCoexistWithEveryAstroProfile() throws Exception {
        JSONObject base=index("ir/mi/index.json"),stb=index("ir/mi_stb/index.json"),apk=index("ir/mi_apk/index.json"),order=index("ir/matching_order.json");
        IrCatalog catalog=new IrCatalog(base,stb,apk,order,path->new FileInputStream("src/main/assets/"+path));
        int extra=apk.getJSONArray("profiles").length();assertTrue("No APK signals imported",extra>0);
        assertEquals(3711+extra,catalog.profileCount());assertEquals(3706,base.getJSONArray("profiles").length());
        int commands=0;
        for(int i=0;i<extra;i++) {
            JSONObject metadata=apk.getJSONArray("profiles").getJSONObject(i),loaded=catalog.loadProfile(metadata.getString("id"));
            assertEquals(metadata.getString("model"),loaded.getString("model"));
            JSONObject signals=loaded.getJSONObject("commands");assertEquals(metadata.getInt("commandCount"),signals.length());
            java.util.Iterator<String> keys=signals.keys();while(keys.hasNext()) {
                String key=keys.next();assertEquals(key,signals.getJSONObject(key).getString("sourceKey"));
                IrCatalog.checkedPattern(signals.getJSONObject(key));commands++;
            }
        }
        assertEquals(apk.getJSONObject("coverage").getInt("commands"),commands);
        assertEquals(141165+commands,catalog.coverage().getInt("commands"));
        List<JSONObject> daewoo=catalog.forBrand("Daewoo","tv");assertTrue(daewoo.size()>=13);
        assertEquals("xm_1_3399",daewoo.get(0).getJSONObject("source").getString("modelId"));
        List<String> astroModels=new ArrayList<>();int astroCommands=0;
        for(JSONObject metadata:catalog.forBrand("Astro","set_top_box")) {
            astroModels.add(metadata.getString("model"));astroCommands+=catalog.loadProfile(metadata.getString("id")).getJSONObject("commands").length();
        }
        assertEquals(Arrays.asList("kk_2_3833_536","kk_2_3833_1819","kk_2_3833_350","kk_2_3833_211","kk_2_3833_2160"),astroModels);
        assertEquals(216,astroCommands);
    }
}
