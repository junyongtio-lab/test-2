package com.universalremote.app;

import org.json.JSONObject;
import org.junit.Test;
import java.io.FileInputStream;
import static org.junit.Assert.*;

public final class AcGuidedCatalogTest {
    private JSONObject read(String path) throws Exception {
        try(FileInputStream in=new FileInputStream("src/main/assets/"+path)) {
            return new JSONObject(IrCatalog.readBounded(in,16*1024*1024));
        }
    }
    private AcGuidedCatalog guide() throws Exception {
        IrCatalog raw=new IrCatalog(read("ir/mi/index.json"),read("ir/mi_stb/index.json"),
            read("ir/mi_apk/index.json"),read("ir/matching_order.json"),p->new FileInputStream("src/main/assets/"+p));
        AcSourceDatabase source=new AcSourceDatabase(read("ir/mi_ac_database/index.json"),p->new FileInputStream("src/main/assets/"+p));
        return new AcGuidedCatalog(raw,source,new PanasonicAcEncoder(read("ir/panasonic_ac/index.json")),new FamilyAcEncoder(source));
    }
    @Test public void everyBrandReportsSendableCandidatesOrExplicitEncoderGap() throws Exception {
        AcGuidedCatalog guide=guide();assertEquals(64,guide.brands().size());
        int availableBrands=0,unavailableBrands=0,recorded=0,settings=0,unavailable=0;
        for(String brand:guide.brands()) {
            recorded+=guide.recorded(brand).size();settings+=guide.settings(brand).size();
            unavailable+=guide.unavailableSourceCount(brand);
            if(guide.sendableCount(brand)>0)availableBrands++;
            else {
                unavailableBrands++;assertTrue(brand,guide.unavailableSourceCount(brand)>0);
                assertFalse(guide.source.profiles(brand).isEmpty());
            }
        }
        assertEquals(64,availableBrands);assertEquals(0,unavailableBrands);
        assertEquals(101,recorded);assertEquals(150,settings);assertEquals(624,unavailable);
        assertTrue(guide.sendableCount("Midea")>0);assertTrue(guide.sendableCount("Sharp")>0);
        assertTrue(guide.settings("Panasonic").size()>=6);assertTrue(guide.settings("LG").size()>1);
    }
    @Test public void everyOfferedCandidateLoadsOrEncodesAndRemainsInItsSelectedBrand() throws Exception {
        AcGuidedCatalog guide=guide();int commands=0,encoded=0;
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(String brand:guide.brands()) {
            for(JSONObject profile:guide.recorded(brand)) {
                assertTrue(profile.getString("brand").equalsIgnoreCase(brand));
                assertTrue(ids.add(profile.getString("id")));
                JSONObject loaded=guide.recorded.loadProfile(profile.getString("id"));
                commands+=loaded.getJSONObject("commands").length();
            }
            for(JSONObject profile:guide.settings(brand)) {
                assertTrue(profile.getString("brand").equalsIgnoreCase(brand));
                assertTrue(ids.add(profile.getString("id")));
                PanasonicAcEncoder.State state=PanasonicAcEncoder.defaults(profile);
                PanasonicAcEncoder.Signal signal=profile.has("family")?FamilyAcEncoder.encode(profile,state,true):PanasonicAcEncoder.encode(profile,state,true);
                IrCatalog.validateSignal(signal.carrierHz,signal.pattern);encoded++;
            }
        }
        assertEquals(251,ids.size());assertEquals(1004,commands);assertEquals(150,encoded);
    }
}
