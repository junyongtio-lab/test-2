package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public final class AcSourceDatabaseTest {
    private JSONObject index() throws Exception {return read("ir/mi_ac_database/index.json");}
    private JSONObject read(String path) throws Exception {try(FileInputStream in=new FileInputStream("src/main/assets/"+path)){return new JSONObject(IrCatalog.readBounded(in,16*1024*1024));}}
    private AcSourceDatabase database(JSONObject index) throws Exception {return new AcSourceDatabase(index,p->new FileInputStream("src/main/assets/"+p));}
    private interface Operation {void run() throws Exception;}
    private void rejects(Operation operation) throws Exception {
        try{operation.run();fail("Invalid AC source data was accepted");}catch(IllegalArgumentException|org.json.JSONException expected){}
    }
    @Test public void everyOneOf734SourceRecordsLoadsWithAllReferencedData() throws Exception {
        AcSourceDatabase db=database(index());assertEquals(734,db.size());assertEquals(45,db.brands().size());
        int raw=0,stateful=0,scripts=0;
        for(JSONObject p:db.profiles(null)) {
            JSONObject record=db.readRecord(p.getString("id"));
            assertEquals(p.getString("model"),record.getJSONObject("sourceRow").getString("ID"));
            assertTrue(record.has("brandSourceRow"));assertTrue(record.has("brandData"));assertTrue(record.has("referencedSourceRow"));
            assertFalse(record.getBoolean("programsExecuted"));
            if(p.getString("definitionKind").equals("recorded"))raw++;else stateful++;
            if(record.getJSONObject("definition").has("1518"))scripts++;
        }
        assertEquals(33,raw);assertEquals(701,stateful);assertEquals(341,scripts);
    }
    @Test public void availableControlsLinkToTheExactExistingSourceProfiles() throws Exception {
        AcSourceDatabase db=database(index());
        IrCatalog ir=new IrCatalog(read("ir/mi/index.json"),read("ir/mi_stb/index.json"),read("ir/mi_apk/index.json"),read("ir/matching_order.json"),p->new FileInputStream("src/main/assets/"+p));
        PanasonicAcEncoder ac=new PanasonicAcEncoder(read("ir/panasonic_ac/index.json"));int raw=0,pana=0,unavailable=0;
        for(JSONObject p:db.profiles(null)) {
            JSONObject control=p.getJSONObject("control");String kind=control.getString("kind");
            if(kind.equals("recorded")) {
                JSONObject target=ir.find(control.getString("profile"));assertNotNull(target);
                assertEquals(p.getString("model"),target.getString("model"));
                assertEquals(p.getString("sourceRecordSha256"),target.getJSONObject("source").getString("sourceSha256"));raw++;
            }else if(kind.equals("panasonic216")) {
                assertEquals(p.getString("model"),ac.find(control.getString("profile")).getString("model"));pana++;
            }else {assertFalse(control.has("profile"));unavailable++;}
        }
        assertEquals(33,raw);assertEquals(3,pana);assertEquals(698,unavailable);
    }
    @Test public void incorrectSourceAndIncompleteCoverageAreRejected() throws Exception {
        for(String key:new String[]{"versionName","versionCode","apkSha256","databaseSha256","archiveSha256"}) {
            JSONObject root=index();root.getJSONObject("source").put(key,key.equals("versionCode")?0:"incorrect");rejects(()->database(root));
        }
        JSONObject root=index();root.getJSONArray("profiles").remove(0);rejects(()->database(root));
        JSONObject count=index();count.getJSONObject("coverage").put("sourceRecordsOmitted",1);rejects(()->database(count));
    }
    @Test public void traversalDuplicateAndFalseSupportMetadataAreRejected() throws Exception {
        JSONObject path=index();path.getJSONArray("profiles").getJSONObject(0).put("asset","../../other.json");rejects(()->database(path));
        JSONObject duplicate=index();JSONArray a=duplicate.getJSONArray("profiles");a.put(a.getJSONObject(0));rejects(()->database(duplicate));
        JSONObject support=index();for(int i=0;i<support.getJSONArray("profiles").length();i++) {
            JSONObject p=support.getJSONArray("profiles").getJSONObject(i);
            if(p.getJSONObject("control").getString("kind").equals("unavailable")){p.getJSONObject("control").put("kind","recorded");break;}
        }
        rejects(()->database(support));
    }
    @Test public void sourceAssetTamperingIsRejectedBeforeParsing() throws Exception {
        JSONObject root=index();JSONObject p=root.getJSONArray("profiles").getJSONObject(0);
        String original=read(p.getString("asset")).toString();
        AcSourceDatabase db=new AcSourceDatabase(root,path->new ByteArrayInputStream((original+" ").getBytes(StandardCharsets.UTF_8)));
        rejects(()->db.readRecord(p.getString("id")));
    }
    @Test public void assetIdentityAndSourceReferenceRemainBoundToMetadata() throws Exception {
        for(String mutation:new String[]{"model","sourceRow","referencedSourceRow","programsExecuted"}) {
            JSONObject root=index(),p=root.getJSONArray("profiles").getJSONObject(0),payload=read(p.getString("asset"));
            if(mutation.equals("model"))payload.put("model","different");
            else if(mutation.equals("sourceRow"))payload.getJSONObject("sourceRow").put("DEVICE_ID",1);
            else if(mutation.equals("referencedSourceRow"))payload.getJSONObject("referencedSourceRow").put("ID","different");
            else payload.put("programsExecuted",true);
            String raw=payload.toString();p.put("assetSha256",IrCatalog.sha256(raw));
            AcSourceDatabase db=new AcSourceDatabase(root,path->new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
            rejects(()->db.readRecord(p.getString("id")));
        }
    }
    @Test public void sourceProgramTextIsReturnedAsUnmodifiedData() throws Exception {
        AcSourceDatabase db=database(index());
        for(JSONObject p:db.profiles(null)) {
            JSONObject record=db.readRecord(p.getString("id")),definition=record.getJSONObject("definition");
            if(definition.has("1518")) {
                assertTrue(definition.get("1518") instanceof String);
                assertEquals(read(p.getString("asset")).getJSONObject("definition").getString("1518"),definition.getString("1518"));
                assertFalse(record.getBoolean("programsExecuted"));return;
            }
        }
        fail("Source hook text was omitted");
    }
    @Test public void unknownIdentityAndOversizedAssetsAreRejected() throws Exception {
        JSONObject root=index();AcSourceDatabase db=database(root);rejects(()->db.readRecord("missing"));
        byte[] large=new byte[512*1024+1];java.util.Arrays.fill(large,(byte)' ');
        AcSourceDatabase oversized=new AcSourceDatabase(root,path->new ByteArrayInputStream(large));
        rejects(()->oversized.readRecord(root.getJSONArray("profiles").getJSONObject(0).getString("id")));
    }
}
