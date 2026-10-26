package com.universalremote.app;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** Exercises the actual limited catalogue loaders and encoders, including exclusion gates. */
public final class DemoCatalogueTest {
    private JSONObject read(String path) throws Exception {
        try(InputStream in=new FileInputStream("src/main/assets/"+path)){return new JSONObject(IrCatalog.readBounded(in,16*1024*1024));}
    }
    private IrCatalog raw() throws Exception {
        return new IrCatalog(read("ir/mi/index.json"),read("ir/mi_stb/index.json"),read("ir/mi_apk/index.json"),read("ir/matching_order.json"),p->new FileInputStream("src/main/assets/"+p));
    }
    private AcSourceDatabase source() throws Exception {
        return new AcSourceDatabase(read("ir/mi_ac_database/index.json"),p->new FileInputStream("src/main/assets/"+p));
    }
    private Set<String> names(String... values){return new TreeSet<>(Arrays.asList(values));}
    @Test public void onlyRequestedBrandsAreLoadableAndAllTheirRecordedSignalsAreIntact() throws Exception {
        IrCatalog raw=raw();assertEquals(names("Samsung"),raw.brands("tv"));assertEquals(names("Panasonic"),raw.brands("air_conditioner"));
        assertEquals(names("KDK","Panasonic"),raw.brands("fan"));assertEquals(names("Astro"),raw.brands("set_top_box"));assertEquals(84,raw.profileCount());
        int commands=0;
        for(String category:Arrays.asList("tv","air_conditioner","fan","set_top_box"))for(String brand:raw.brands(category)){
            assertTrue(DemoScope.allowed(category,brand));
            for(JSONObject p:raw.forBrand(brand,category)){
                JSONObject loaded=raw.loadProfile(p.getString("id"));assertEquals(brand,loaded.getString("brand"));
                JSONObject signals=loaded.getJSONObject("commands");Iterator<String> keys=signals.keys();
                while(keys.hasNext()){
                    JSONObject signal=signals.getJSONObject(keys.next());JSONArray pattern=signal.getJSONArray("pattern");int[] pulses=new int[pattern.length()];for(int i=0;i<pulses.length;i++)pulses[i]=pattern.getInt(i);
                    IrCatalog.validateSignal(signal.getInt("carrierHz"),pulses);commands++;
                }
            }
        }
        assertTrue(commands>1000);
        assertFalse(DemoScope.allowed("tv","LG"));assertFalse(DemoScope.allowed("tv","Panasonic"));assertFalse(DemoScope.allowed("fan","Midea"));assertFalse(DemoScope.allowed("air_conditioner","Samsung"));
        assertTrue(raw.forBrand("LG","tv").isEmpty());assertTrue(raw.forBrand("Samsung","air_conditioner").isEmpty());
    }
    @Test public void acGuideContainsOnlyPanasonicAndMideaAndEveryCandidateEncodes() throws Exception {
        IrCatalog raw=raw();AcSourceDatabase source=source();FamilyAcEncoder family=new FamilyAcEncoder(source);PanasonicAcEncoder pana=new PanasonicAcEncoder(read("ir/panasonic_ac/index.json"));
        AcGuidedCatalog guide=new AcGuidedCatalog(raw,source,pana,family);assertEquals(names("Midea","Panasonic"),guide.brands());assertEquals(46,source.size());assertEquals(4,family.profiles().size());
        for(JSONObject p:source.profiles(null)){assertTrue(DemoScope.allowed("air_conditioner",p.getString("brand")));source.readRecord(p.getString("id"));}
        int count=0;for(String brand:guide.brands()){
            assertTrue(guide.sendableCount(brand)>0);
            for(JSONObject p:guide.settings(brand)){
                PanasonicAcEncoder.State request=PanasonicAcEncoder.defaults(p);
                PanasonicAcEncoder.Signal signal=p.has("family")?FamilyAcEncoder.encode(p,request,false):PanasonicAcEncoder.encode(p,request,false);
                IrCatalog.validateSignal(signal.carrierHz,signal.pattern);count++;
            }
        }
        assertEquals(10,count);assertEquals(12,guide.sendableCount("Panasonic"));assertEquals(2,guide.sendableCount("Midea"));
        assertNull(family.find("22_3577"));assertFalse(FamilyAcEncoder.supports("125_2807"));
    }
    @Test public void retainedAc112ControlsStillMatchVersion166PacketFixtures() throws Exception {
        FamilyAcEncoder encoder=new FamilyAcEncoder(source());int cases=0;
        for(String line:Files.readAllLines(Paths.get("src/test/resources/ac112-controls.csv"))){
            String[] f=line.split(",",-1);for(int variant=0;variant<2;variant++){
                JSONObject p=encoder.find(f[0]+(variant==0?"":"_tcl112"));assertNotNull(p);
                PanasonicAcEncoder.State s=new PanasonicAcEncoder.State(f[2].equals("1"),f[1].equals("1")?"heat":"cool",24,Integer.parseInt(f[3]),Integer.parseInt(f[4]));
                if(!f[5].isEmpty())for(String pair:f[5].split(";")){String[] kv=pair.split("=");s.extras.put(kv[0],Integer.parseInt(kv[1]));}
                assertArrayEquals(PanasonicAcEncoder.hex(f[6]),FamilyAcEncoder.encode(p,s,false).packet);cases++;
            }
        }
        assertTrue(cases>500);
    }
}
