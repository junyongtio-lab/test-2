package com.universalremote.app;

import org.json.JSONObject;
import org.junit.Test;
import java.io.FileInputStream;
import java.util.List;
import static org.junit.Assert.*;

public final class TvGuidedTestingTest {
    @Test public void confirmationRequiresTwoDifferentSentButtonsOnTheSameCandidate() {
        TvTestSession session=new TvTestSession();
        session.confirm();assertEquals(0,session.count());assertFalse(session.canSave());
        session.sent("vol+");session.confirm();assertFalse(session.canSave());
        session.sent("vol+");session.confirm();assertEquals(1,session.count());
        session.sending();session.confirm();assertEquals(1,session.count());
        session.sent("power");session.confirm();assertTrue(session.canSave());
        session.reset();assertEquals(0,session.count());assertFalse(session.canSave());
        session.sent("mute");session.reset();session.confirm();assertFalse(session.canSave());
    }
    private JSONObject index(String path) throws Exception {
        try(FileInputStream in=new FileInputStream("src/main/assets/"+path)) {
            return new JSONObject(IrCatalog.readBounded(in,16*1024*1024));
        }
    }
    @Test public void everySelectableTvBrandHasCandidatesThatSupportTwoButtonGuidedTesting() throws Exception {
        IrCatalog catalog=new IrCatalog(index("ir/mi/index.json"),index("ir/mi_stb/index.json"),
            index("ir/mi_apk/index.json"),index("ir/matching_order.json"),
            path->new FileInputStream("src/main/assets/"+path));
        int candidatesChecked=0;
        java.util.Set<String> ids=new java.util.HashSet<>();
        assertEquals(1020,catalog.brands("tv").size());
        for(String brand:catalog.brands("tv")) {
            List<JSONObject> candidates=catalog.forBrand(brand,"tv");
            assertFalse(brand,candidates.isEmpty());
            for(JSONObject metadata:candidates) {
                assertTrue("Duplicate selectable candidate: "+metadata,ids.add(metadata.getString("id")));
                JSONObject commands=catalog.loadProfile(metadata.getString("id")).getJSONObject("commands");
                assertTrue(brand+" / "+metadata.getString("model"),commands.length()>=2);
                candidatesChecked++;
            }
        }
        assertEquals(3381,candidatesChecked);
    }
    @Test public void allSixBrandsHaveLoadableGuidedCandidatesAndUsefulTestButtons() throws Exception {
        IrCatalog catalog=new IrCatalog(index("ir/mi/index.json"),index("ir/mi_stb/index.json"),
            index("ir/mi_apk/index.json"),index("ir/matching_order.json"),
            path->new FileInputStream("src/main/assets/"+path));
        String[] brands={"Samsung","LG","Hisense","Panasonic","Sharp","Xiaomi"};
        int[] counts={69,35,60,30,36,1};
        for(int i=0;i<brands.length;i++) {
            List<JSONObject> candidates=catalog.forBrand(brands[i],"tv");
            assertEquals(brands[i],counts[i],candidates.size());
            for(JSONObject metadata:candidates) {
                JSONObject commands=catalog.loadProfile(metadata.getString("id")).getJSONObject("commands");
                assertTrue(metadata.getString("model"),commands.length()>=2);
            }
            JSONObject commands=catalog.loadProfile(candidates.get(0).getString("id")).getJSONObject("commands");
            java.util.List<String> keys=new java.util.ArrayList<>();commands.keys().forEachRemaining(keys::add);
            RecordedControls controls=new RecordedControls(keys);
            assertNotNull(brands[i]+" power",controls.key("power"));
            assertNotNull(brands[i]+" volume",controls.key("volume_up"));
        }
    }
}
