package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class MiMigrationTest {
    private JSONObject device(String id,String mode,String profile) throws Exception {
        return new JSONObject().put("id",id).put("mode",mode).put("profile",profile).put("name","Owner's custom name");
    }
    @Test public void obsoleteIrIsRemovedWhileWifiMetadataAndCurrentMiProfilesArePreserved() throws Exception {
        JSONObject lg=device("lg-id","lg","").put("host","192.168.1.20");
        JSONObject samsung=device("samsung-id","smartthings","").put("cloudId","manufacturer-device-id");
        JSONObject current=device("current-ir","ir","mi_current");
        JSONArray saved=new JSONArray().put(device("old-astro","ir","astro_ultrabox_msb"))
            .put(lg).put(device("old-ac","ac","daikin_native")).put(samsung).put(current)
            .put(device("old-import","ir","import_old"));
        MiCatalogueMigration.Result result=MiCatalogueMigration.removeUnavailable(saved,"mi_current"::equals);
        assertEquals(3,result.removedCount);assertEquals(3,result.retained.length());assertEquals(6,saved.length());
        assertSame(lg,result.retained.getJSONObject(0));assertSame(samsung,result.retained.getJSONObject(1));assertSame(current,result.retained.getJSONObject(2));
        assertEquals("192.168.1.20",result.retained.getJSONObject(0).getString("host"));
        assertEquals("manufacturer-device-id",result.retained.getJSONObject(1).getString("cloudId"));
        assertTrue(result.removedIds.contains("old-astro"));assertTrue(result.removedIds.contains("old-ac"));
        assertTrue(result.removedIds.contains("old-import"));assertFalse(result.removedIds.contains("lg-id"));assertFalse(result.removedIds.contains("samsung-id"));
    }
    @Test public void migrationIsIdempotentAndEmptyProfileReferencesDoNotSurvive() throws Exception {
        MiCatalogueMigration.Result first=MiCatalogueMigration.removeUnavailable(new JSONArray()
            .put(device("old","ir","")).put(device("lg","lg","")),id->false);
        MiCatalogueMigration.Result second=MiCatalogueMigration.removeUnavailable(first.retained,id->false);
        assertEquals(1,first.removedCount);assertEquals(0,second.removedCount);assertEquals(1,second.retained.length());
    }
    @Test public void malformedStoredArrayAbortsWithoutMutatingItsInput() throws Exception {
        JSONArray saved=new JSONArray().put(device("old","ir","legacy")).put("broken-entry");String original=saved.toString();
        try{MiCatalogueMigration.removeUnavailable(saved,id->false);fail("Malformed entry accepted");}
        catch(org.json.JSONException expected){assertEquals(original,saved.toString());}
    }
    @Test public void duplicateIdsDoNotDeleteCredentialsUsedByARetainedWifiDevice() throws Exception {
        MiCatalogueMigration.Result result=MiCatalogueMigration.removeUnavailable(new JSONArray()
            .put(device("shared-id","ir","old")).put(device("shared-id","lg","")),id->false);
        assertEquals(1,result.removedCount);assertEquals(1,result.retained.length());assertTrue(result.removedIds.isEmpty());
    }
}
