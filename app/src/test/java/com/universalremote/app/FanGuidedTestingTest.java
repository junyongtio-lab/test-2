package com.universalremote.app;

import org.json.JSONObject;
import org.junit.Test;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public final class FanGuidedTestingTest {
    private JSONObject index(String path) throws Exception {
        try(FileInputStream in=new FileInputStream("src/main/assets/"+path)) {
            return new JSONObject(IrCatalog.readBounded(in,16*1024*1024));
        }
    }
    @Test public void everyFanBrandHasLoadableCandidatesAndSingleButtonLimitsRemainVisible() throws Exception {
        IrCatalog catalog=new IrCatalog(index("ir/mi/index.json"),index("ir/mi_stb/index.json"),
            index("ir/mi_apk/index.json"),index("ir/matching_order.json"),
            path->new FileInputStream("src/main/assets/"+path));
        Set<String> ids=new HashSet<>();int commandCount=0,singleButtons=0;
        assertEquals(133,catalog.brands("fan").size());
        for(String brand:catalog.brands("fan")) {
            assertFalse(brand,catalog.forBrand(brand,"fan").isEmpty());
            for(JSONObject metadata:catalog.forBrand(brand,"fan")) {
                assertEquals("fan",metadata.getString("category"));
                assertTrue(ids.add(metadata.getString("id")));
                JSONObject commands=catalog.loadProfile(metadata.getString("id")).getJSONObject("commands");
                assertTrue(brand,commands.length()>=1);commandCount+=commands.length();
                TvTestSession responses=new TvTestSession();responses.reset(Math.min(2,commands.length()));
                assertEquals(Math.min(2,commands.length()),responses.requiredCount());
                if(commands.length()==1) {
                    singleButtons++;
                    String key=commands.keys().next();responses.sent(key);responses.confirm();assertTrue(responses.canSave());
                    if(metadata.getString("model").equals("mx_6_41_12"))assertEquals("shake_wind",key);
                    if(metadata.getString("model").equals("mx_6_127_34"))assertEquals("timer",key);
                }
            }
        }
        assertEquals(422,ids.size());assertEquals(4181,commandCount);assertEquals(6,singleButtons);
    }
    @Test public void singleButtonConfirmationNeverCarriesIntoAnotherCandidate() {
        TvTestSession responses=new TvTestSession();responses.reset(1);
        responses.confirm();assertFalse(responses.canSave());
        responses.sent("power");responses.sending();responses.confirm();assertFalse(responses.canSave());
        responses.sent("power");responses.confirm();assertTrue(responses.canSave());
        responses.reset();assertEquals(2,responses.requiredCount());assertFalse(responses.canSave());
        responses.sent("power");responses.confirm();assertFalse(responses.canSave());
        responses.sent("power");responses.confirm();assertFalse(responses.canSave());
        responses.sent("fanspeed");responses.confirm();assertTrue(responses.canSave());
        responses.reset(1);assertFalse(responses.canSave());assertFalse(responses.awaitingResponse());
    }
}
