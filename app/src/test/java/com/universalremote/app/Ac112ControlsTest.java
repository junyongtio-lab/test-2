package com.universalremote.app;

import org.json.JSONObject;
import org.junit.Test;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class Ac112ControlsTest {
    private FamilyAcEncoder encoder() throws Exception {
        try(FileInputStream in=new FileInputStream("src/main/assets/ir/mi_ac_database/index.json")){
            return new FamilyAcEncoder(new AcSourceDatabase(new JSONObject(IrCatalog.readBounded(in,1024*1024)),p->new FileInputStream("src/main/assets/"+p)));
        }
    }
    @Test public void additionalControlsMatchIndependentSourceTableFixturesAndExportWaveforms() throws Exception {
        FamilyAcEncoder encoder=encoder();List<String> output=new ArrayList<>();int count=0;
        String destination=System.getenv("PALM_AC112_CONTROLS_WAVEFORMS");
        for(String line:Files.readAllLines(Paths.get("../tools/ac_expanded/control-vectors.csv"))){
            String[] f=line.split(",",-1);
            for(int variant=0;variant<2;variant++){
                JSONObject p=encoder.find(f[0]+(variant==0?"":"_tcl112"));String before=p.toString();
                PanasonicAcEncoder.State s=new PanasonicAcEncoder.State(f[2].equals("1"),f[1].equals("1")?"heat":"cool",24,Integer.parseInt(f[3]),Integer.parseInt(f[4]));
                if(!f[5].isEmpty())for(String pair:f[5].split(";")){String[] kv=pair.split("=");s.extras.put(kv[0],Integer.parseInt(kv[1]));}
                String requested=s.toJson().toString();PanasonicAcEncoder.Signal signal=FamilyAcEncoder.encode(p,s,false);
                assertArrayEquals(line,PanasonicAcEncoder.hex(f[6]),signal.packet);assertEquals(before,p.toString());assertEquals(requested,s.toJson().toString());
                assertEquals(228,signal.pattern.length);assertEquals(38000,signal.carrierHz);
                if(destination!=null){StringBuilder row=new StringBuilder(variant+","+f[6]+",38000");for(int duration:signal.pattern)row.append(',').append(duration);output.add(row.toString());}
                count++;
            }
        }
        assertEquals(29664,count);if(destination!=null)Files.write(Paths.get(destination),output);
    }
    @Test public void capabilitiesAreSourceSpecificAndUnsupportedRequestsAreRejected() throws Exception {
        FamilyAcEncoder encoder=encoder();int fan=0,swing=0,timers=0,special=0;
        for(JSONObject p:encoder.profiles())if(p.optBoolean("expanded")&&p.getInt("variant")==0){
            JSONObject c=p.getJSONObject("additionalControls");fan++;swing++;if(c.getInt("timerHook")>0)timers++;if(c.getJSONObject("extras").length()>0)special++;
            PanasonicAcEncoder.State invalid=PanasonicAcEncoder.defaults(p);invalid.swing=c.getJSONArray("swingLabels").length();reject(p,invalid);
            invalid=PanasonicAcEncoder.defaults(p);invalid.extras.put("99",1);reject(p,invalid);
            invalid=PanasonicAcEncoder.defaults(p);invalid.extras.put("9",1);reject(p,invalid);
            invalid=PanasonicAcEncoder.defaults(p);invalid.extras.put("9",0).put("10",0);reject(p,invalid);
            invalid=PanasonicAcEncoder.defaults(p);invalid.extras.put("9",1441);reject(p,invalid);
            if(c.getInt("timerHook")==0){invalid=PanasonicAcEncoder.defaults(p);invalid.extras.put("9",0);reject(p,invalid);}
            JSONObject features=c.getJSONObject("extras");java.util.Iterator<String> keys=features.keys();
            while(keys.hasNext()){
                String fid=keys.next();invalid=PanasonicAcEncoder.defaults(p);invalid.extras.put(fid,0.5);reject(p,invalid);
                invalid=PanasonicAcEncoder.defaults(p);invalid.extras.put(fid,99);reject(p,invalid);
                JSONObject feature=features.getJSONObject(fid);
                if(!feature.getString("modes").contains("C")){invalid=PanasonicAcEncoder.defaults(p);invalid.extras.put(fid,1);reject(p,invalid);}
            }
            assertFalse(p.getBoolean("hardwareTested"));
        }
        assertEquals(70,fan);assertEquals(70,swing);assertEquals(56,timers);assertEquals(56,special);
    }
    private void reject(JSONObject profile,PanasonicAcEncoder.State state) throws Exception {
        try{FamilyAcEncoder.encode(profile,state,false);fail("Unsupported control accepted: "+state.toJson());}catch(IllegalArgumentException expected){}
    }
    @Test public void savedStateKeepsSpecialsAndDoesNotRepeatOneShotTimers() throws Exception {
        PanasonicAcEncoder.State old=new PanasonicAcEncoder.State(false,"heat",23,3,6);old.extras.put("22",1);
        PanasonicAcEncoder.State sent=old.copy();sent.power=true;sent.extras.put("9",60);
        PanasonicAcEncoder.State saved=Ac112Controls.persistentRequest(sent,old);
        assertFalse(saved.power);assertFalse(saved.extras.has("9"));assertFalse(saved.extras.has("10"));assertEquals(1,saved.extras.getInt("22"));
        PanasonicAcEncoder.State restored=PanasonicAcEncoder.State.fromJson(saved.toJson());
        assertEquals(saved.toJson().toString(),restored.toJson().toString());
        sent.extras.put("22",0);assertEquals(1,saved.extras.getInt("22"));
        PanasonicAcEncoder.State normal=Ac112Controls.persistentRequest(old,old);assertEquals(old.toJson().toString(),normal.toJson().toString());
    }
}
