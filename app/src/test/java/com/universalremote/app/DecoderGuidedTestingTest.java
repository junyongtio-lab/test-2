package com.universalremote.app;

import org.json.JSONObject;
import org.junit.Test;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public final class DecoderGuidedTestingTest {
    private JSONObject index(String path) throws Exception {
        try(FileInputStream in=new FileInputStream("src/main/assets/"+path)) {
            return new JSONObject(IrCatalog.readBounded(in,16*1024*1024));
        }
    }
    @Test public void everyDecoderBrandHasLoadableCandidatesForTwoButtonGuidedTesting() throws Exception {
        IrCatalog catalog=new IrCatalog(index("ir/mi/index.json"),index("ir/mi_stb/index.json"),
            index("ir/mi_apk/index.json"),index("ir/matching_order.json"),
            path->new FileInputStream("src/main/assets/"+path));
        Set<String> ids=new HashSet<>();int commandsChecked=0;
        assertEquals(182,catalog.brands("set_top_box").size());
        assertEquals(5,catalog.forBrand("Astro","set_top_box").size());
        for(String brand:catalog.brands("set_top_box")) {
            List<JSONObject> candidates=catalog.forBrand(brand,"set_top_box");
            assertFalse(brand,candidates.isEmpty());
            for(JSONObject metadata:candidates) {
                assertEquals("set_top_box",metadata.getString("category"));
                assertTrue("Repeated candidate: "+metadata,ids.add(metadata.getString("id")));
                JSONObject commands=catalog.loadProfile(metadata.getString("id")).getJSONObject("commands");
                assertTrue(brand+" / "+metadata.getString("model"),commands.length()>=2);
                commandsChecked+=commands.length();
            }
        }
        assertEquals(411,ids.size());assertEquals(12416,commandsChecked);
    }
}
