package com.universalremote.app;

import org.json.JSONObject;
import org.junit.Test;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class ExpandedAcEncoderTest {
    private AcSourceDatabase database() throws Exception {
        try(FileInputStream in=new FileInputStream("src/main/assets/ir/mi_ac_database/index.json")) {
            return new AcSourceDatabase(new JSONObject(IrCatalog.readBounded(in,1024*1024)),p->new FileInputStream("src/main/assets/"+p));
        }
    }
    @Test public void packetsMatchBothIndependentEncodersAndAllWaveformsCanBeExported() throws Exception {
        FamilyAcEncoder encoder=new FamilyAcEncoder(database());List<String> waveforms=new ArrayList<>();int cases=0;
        String destination=System.getenv("PALM_EXPANDED_WAVEFORMS");
        for(String row:Files.readAllLines(Paths.get("../tools/ac_expanded/oracle.csv"))) {
            String[] f=row.split(",");
            for(int variant=0;variant<2;variant++) {
                JSONObject p=encoder.find(f[0]+(variant==0?"":"_tcl112"));assertNotNull(p);
                String original=p.toString();
                PanasonicAcEncoder.State state=new PanasonicAcEncoder.State(f[3].equals("1"),f[1].equals("1")?"heat":"cool",Integer.parseInt(f[2]),-1,-1);
                PanasonicAcEncoder.Signal signal=FamilyAcEncoder.encode(p,state,true);
                assertArrayEquals(row,PanasonicAcEncoder.hex(f[4]),signal.packet);assertEquals(original,p.toString());
                assertEquals(38000,signal.carrierHz);assertEquals(228,signal.pattern.length);
                if(destination!=null) {
                    StringBuilder out=new StringBuilder(variant+","+f[4]+",38000");
                    for(int duration:signal.pattern)out.append(',').append(duration);waveforms.add(out.toString());
                }
                cases++;
            }
        }
        assertEquals(8400,cases);
        if(destination!=null)Files.write(Paths.get(destination),waveforms);
    }
    @Test public void unverifiedDefinitionsAndUnsupportedControlsAreNeverEnabled() throws Exception {
        FamilyAcEncoder encoder=new FamilyAcEncoder(database());int count=0;
        for(JSONObject p:encoder.profiles())if(p.optBoolean("expanded")) {
            assertFalse(p.getBoolean("supportsFan"));assertFalse(p.getBoolean("supportsSwing"));
            assertFalse(p.getBoolean("hardwareTested"));assertEquals(2,p.getJSONArray("modes").length());
            PanasonicAcEncoder.State invalid=PanasonicAcEncoder.defaults(p);invalid.fan=0;
            try{FamilyAcEncoder.encode(p,invalid,true);fail("Unimplemented fan control accepted");}catch(IllegalArgumentException expected){}
            JSONObject tampered=new JSONObject(p.toString());tampered.getJSONObject("definition").put("300","1,1");
            try{FamilyAcEncoder.encode(tampered,PanasonicAcEncoder.defaults(p),true);fail("Changed definition accepted");}catch(IllegalArgumentException expected){}
            count++;
        }
        assertEquals(140,count);assertFalse(ExpandedAcEncoder.supports("unknown"));
    }
}
