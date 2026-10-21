package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** A new AC selection must survive the raw-catalogue migration on next launch. */
public final class PanasonicSelectionPersistenceTest {
    @Test public void encodedAcStateAndWorkingAstroSurviveRawCatalogueMigration() throws Exception {
        JSONObject request=new JSONObject().put("power",true).put("mode","cool")
            .put("temperature",25).put("fan",0).put("swing",0);
        JSONObject ac=new JSONObject().put("id","saved-panasonic").put("mode","panasonic_ac")
            .put("profile","pana_137_2587").put("sourceModelId","137_2587")
            .put("requestedState",request).put("responseConfirmed",true);
        JSONObject astro=new JSONObject().put("id","saved-astro").put("mode","ir").put("profile","mi_working_astro");
        JSONObject wifi=new JSONObject().put("id","saved-lg").put("mode","lg").put("host","192.0.2.12");
        JSONArray saved=new JSONArray().put(ac).put(astro).put(wifi)
            .put(new JSONObject().put("id","legacy-ac").put("mode","ac").put("profile","old_native_ac"));
        String originalAc=ac.toString(),originalRequest=request.toString();

        MiCatalogueMigration.Result result=MiCatalogueMigration.removeUnavailable(saved,"mi_working_astro"::equals);

        assertEquals(1,result.removedCount);assertEquals(3,result.retained.length());
        assertSame(ac,result.retained.getJSONObject(0));assertSame(astro,result.retained.getJSONObject(1));
        assertSame(wifi,result.retained.getJSONObject(2));
        assertEquals(originalAc,result.retained.getJSONObject(0).toString());
        assertEquals(originalRequest,result.retained.getJSONObject(0).getJSONObject("requestedState").toString());
        assertTrue(result.retained.getJSONObject(0).getBoolean("responseConfirmed"));
        assertEquals("legacy-ac",result.removedIds.get(0));
        MiCatalogueMigration.Result again=MiCatalogueMigration.removeUnavailable(result.retained,"mi_working_astro"::equals);
        assertEquals(0,again.removedCount);assertEquals(result.retained.toString(),again.retained.toString());
    }
}
