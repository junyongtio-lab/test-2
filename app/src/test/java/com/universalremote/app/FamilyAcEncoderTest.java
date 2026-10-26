package com.universalremote.app;

import org.json.JSONObject;
import org.junit.Test;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public final class FamilyAcEncoderTest {
    private FamilyAcEncoder encoder() throws Exception {
        JSONObject index;
        try(FileInputStream in=new FileInputStream("src/main/assets/ir/mi_ac_database/index.json")) {
            index=new JSONObject(IrCatalog.readBounded(in,1024*1024));
        }
        return new FamilyAcEncoder(new AcSourceDatabase(index,p->new FileInputStream("src/main/assets/"+p)));
    }
    @Test public void packetsMatchIndependentUpstreamEncodersForEveryExposedSetting() throws Exception {
        FamilyAcEncoder encoder=encoder();int cases=0;
        String destination=System.getenv("PALM_FAMILY_WAVEFORMS");
        java.util.List<String> waveforms=new java.util.ArrayList<>();
        for(String line:Files.readAllLines(Paths.get("../tools/ac_families/oracle.csv"))) {
            String[] fields=line.split(",");int family=Integer.parseInt(fields[0]);
            JSONObject profile=encoder.find(FamilyAcEncoder.MODELS[family]);
            PanasonicAcEncoder.State state=new PanasonicAcEncoder.State(fields[4].equals("1"),fields[1].equals("1")?"heat":"cool",Integer.parseInt(fields[2]),Integer.parseInt(fields[3]),-1);
            String original=profile.toString();PanasonicAcEncoder.Signal signal=FamilyAcEncoder.encode(profile,state,true);
            assertArrayEquals("Upstream oracle case "+line,PanasonicAcEncoder.hex(fields[5]),signal.packet);
            assertEquals(original,profile.toString());assertEquals(profile.getInt("carrierHz"),signal.carrierHz);
            assertEquals(0,signal.pattern.length%2);for(int duration:signal.pattern)assertTrue(duration>0);
            if(family==0){assertEquals(60,signal.pattern.length);assertEquals(8320,signal.pattern[0]);assertEquals(4270,signal.pattern[1]);}
            if(family==1){assertEquals(584,signal.pattern.length);assertEquals(15642,signal.pattern[291]);}
            if(family==2){assertEquals(328,signal.pattern.length);assertEquals(28000,signal.pattern[115]);}
            if(family==3){assertEquals(350,signal.pattern.length);assertEquals(17900,signal.pattern[1]);assertEquals(100000,signal.pattern[349]);}
            if(destination!=null){StringBuilder row=new StringBuilder(fields[0]+","+fields[5]+","+signal.carrierHz);for(int duration:signal.pattern)row.append(",").append(duration);waveforms.add(row.toString());}
            cases++;
        }
        assertEquals(960,cases);
        if(destination!=null)Files.write(Paths.get(destination),waveforms);
    }
    @Test public void unsupportedAndTamperedRequestsNeverGenerateSignals() throws Exception {
        FamilyAcEncoder encoder=encoder();assertEquals(144,encoder.profiles().size());
        assertNull(encoder.find("unknown"));assertFalse(FamilyAcEncoder.supports("137_2587"));
        for(JSONObject profile:encoder.profiles()) {
            for(PanasonicAcEncoder.State invalid:new PanasonicAcEncoder.State[]{
                new PanasonicAcEncoder.State(true,"auto",24,0,-1),new PanasonicAcEncoder.State(true,"cool",15,0,-1),
                new PanasonicAcEncoder.State(true,"heat",31,0,-1),new PanasonicAcEncoder.State(true,"cool",24,4,-1),
                new PanasonicAcEncoder.State(true,"cool",24,0,0)}) {
                try{FamilyAcEncoder.encode(profile,invalid,false);fail("Unsupported request accepted");}catch(IllegalArgumentException expected){}
            }
            JSONObject altered=new JSONObject(profile.toString());altered.getJSONObject("definition").put("1002","0100");
            try{FamilyAcEncoder.encode(altered,PanasonicAcEncoder.defaults(profile),false);fail("Changed template accepted");}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void allFamiliesRetainTheAndroidEmitterAndCarrierGate() throws Exception {
        FamilyAcEncoder encoder=encoder();
        for(JSONObject profile:encoder.profiles()) {
            PanasonicAcEncoder.Signal signal=FamilyAcEncoder.encode(profile,PanasonicAcEncoder.defaults(profile),false);
            final int[] sent={0};
            IrCatalog.Emitter ready=new IrCatalog.Emitter() {
                public boolean hasEmitter(){return true;}
                public int[][] carrierRanges(){return new int[][]{{signal.carrierHz,signal.carrierHz}};}
                public void transmit(int hz,int[] pattern){assertEquals(signal.carrierHz,hz);assertArrayEquals(signal.pattern,pattern);sent[0]++;}
            };
            IrCatalog.transmitSignal(ready,signal.carrierHz,signal.pattern);assertEquals(1,sent[0]);
            IrCatalog.Emitter absent=new IrCatalog.Emitter() {
                public boolean hasEmitter(){return false;}
                public int[][] carrierRanges(){throw new AssertionError("Absent emitter queried");}
                public void transmit(int hz,int[] pattern){throw new AssertionError("Absent emitter transmitted");}
            };
            try{IrCatalog.transmitSignal(absent,signal.carrierHz,signal.pattern);fail("Missing IR hardware accepted");}catch(IllegalStateException expected){}
        }
    }

}
