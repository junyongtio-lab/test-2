package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class RecordedSignalSenderTest {
    private JSONObject signal(String key,int frequency,int marker) throws Exception {
        return new JSONObject().put("sourceKey",key).put("label",key).put("carrierHz",frequency)
            .put("pattern",new JSONArray().put(9000).put(4500).put(marker));
    }
    private JSONObject commands() throws Exception {
        return new JSONObject().put("power",signal("power",38000,560)).put("power_r",signal("power_r",36000,570))
            .put("up",signal("up",38000,580)).put("up_r",signal("up_r",36000,590))
            .put("menu",signal("menu",38000,600));
    }
    private static final class Capture implements RecordedSignalSender.SignalConsumer {
        final List<Integer> frequencies=new ArrayList<>();final List<int[]> patterns=new ArrayList<>();
        @Override public void transmit(int frequency,int[] pattern){frequencies.add(frequency);patterns.add(pattern.clone());}
        void matches(int index,JSONObject command) throws Exception {
            assertEquals(command.getInt("carrierHz"),(int)frequencies.get(index));
            assertArrayEquals(IrCatalog.checkedPattern(command),patterns.get(index));
        }
    }
    @Test public void repeatedOrdinaryPressesAlternateTheCompleteRecordedWaveform() throws Exception {
        RecordedSignalSender sender=new RecordedSignalSender();JSONObject commands=commands();Capture capture=new Capture();
        sender.send("remote","power",commands,capture);sender.send("remote","power",commands,capture);sender.send("remote","power",commands,capture);
        capture.matches(0,commands.getJSONObject("power"));capture.matches(1,commands.getJSONObject("power_r"));capture.matches(2,commands.getJSONObject("power"));
        assertEquals(3,capture.patterns.size());
    }
    @Test public void alternationContinuesAcrossDifferentButtonsWithinOneProfile() throws Exception {
        RecordedSignalSender sender=new RecordedSignalSender();JSONObject commands=commands();Capture capture=new Capture();
        sender.send("remote","power",commands,capture);sender.send("remote","up",commands,capture);sender.send("remote","power",commands,capture);
        capture.matches(0,commands.getJSONObject("power"));capture.matches(1,commands.getJSONObject("up_r"));capture.matches(2,commands.getJSONObject("power"));
    }
    @Test public void missingAlternateFallsBackToNormalAndSuccessfulPressStillAdvancesState() throws Exception {
        RecordedSignalSender sender=new RecordedSignalSender();JSONObject commands=commands();Capture capture=new Capture();
        sender.send("remote","power",commands,capture);sender.send("remote","menu",commands,capture);sender.send("remote","up",commands,capture);
        capture.matches(0,commands.getJSONObject("power"));capture.matches(1,commands.getJSONObject("menu"));capture.matches(2,commands.getJSONObject("up"));
    }
    @Test public void explicitAlternateIsExactAndDoesNotAdvanceAutomaticState() throws Exception {
        RecordedSignalSender sender=new RecordedSignalSender();JSONObject commands=commands();Capture capture=new Capture();
        sender.send("remote","power_r",commands,capture);sender.send("remote","power",commands,capture);
        sender.send("remote","up_r",commands,capture);sender.send("remote","up",commands,capture);
        capture.matches(0,commands.getJSONObject("power_r"));capture.matches(1,commands.getJSONObject("power"));
        capture.matches(2,commands.getJSONObject("up_r"));capture.matches(3,commands.getJSONObject("up_r"));
        JSONObject onlyAlternate=new JSONObject().put("orphan_r",signal("orphan_r",38000,610));
        sender.send("remote","orphan_r",onlyAlternate,capture);capture.matches(4,onlyAlternate.getJSONObject("orphan_r"));
    }
    @Test public void switchingProfilesNeverSharesAlternatingState() throws Exception {
        RecordedSignalSender sender=new RecordedSignalSender();JSONObject commands=commands();Capture capture=new Capture();
        sender.send("first","power",commands,capture);sender.send("second","up",commands,capture);
        sender.send("first","up",commands,capture);sender.send("second","power",commands,capture);
        capture.matches(0,commands.getJSONObject("power"));capture.matches(1,commands.getJSONObject("up"));
        capture.matches(2,commands.getJSONObject("up_r"));capture.matches(3,commands.getJSONObject("power_r"));
    }
    @Test public void missingOrInvalidSignalsDoNotAdvanceStateOrReachHardware() throws Exception {
        RecordedSignalSender sender=new RecordedSignalSender();JSONObject commands=commands();Capture capture=new Capture();
        sender.send("remote","power",commands,capture);
        try{sender.send("remote","missing",commands,capture);fail("Missing source key sent");}
        catch(IllegalArgumentException expected){/* State remains alternate. */}
        JSONObject invalid=new JSONObject(commands.toString());invalid.getJSONObject("up_r").put("pattern",new JSONArray("[9000,-1,560]"));
        try{sender.send("remote","up",invalid,capture);fail("Invalid source timings sent");}
        catch(IllegalArgumentException expected){/* State remains alternate. */}
        assertEquals(1,capture.patterns.size());sender.send("remote","up",commands,capture);capture.matches(1,commands.getJSONObject("up_r"));
    }
    @Test public void emitterCapabilityAndTransmitFailuresNeverConsumeAlternateState() throws Exception {
        JSONObject commands=commands();
        for(String failure:new String[]{"emitter","carrier","transmit"}) {
            RecordedSignalSender sender=new RecordedSignalSender();Capture capture=new Capture();
            sender.send("remote","power",commands,capture);
            IrCatalog.Emitter phone=new IrCatalog.Emitter() {
                @Override public boolean hasEmitter(){return !failure.equals("emitter");}
                @Override public int[][] carrierRanges(){return failure.equals("carrier")?new int[][]{{40000,45000}}:null;}
                @Override public void transmit(int frequency,int[] pattern){throw new IllegalStateException("Transmission failed");}
            };
            try{sender.send("remote","up",commands,(frequency,pattern)->IrCatalog.transmitSignal(phone,frequency,pattern));fail("Expected "+failure+" failure");}
            catch(IllegalStateException expected){/* No successful transmission; still alternate. */}
            sender.send("remote","up",commands,capture);capture.matches(1,commands.getJSONObject("up_r"));
            assertEquals(2,capture.patterns.size());
        }
    }
    private JSONObject read(String asset) throws Exception {
        try(FileInputStream in=new FileInputStream("src/main/assets/"+asset)) {
            return new JSONObject(IrCatalog.readBounded(in,16*1024*1024));
        }
    }
    @Test public void actualAstroPairsSelectOriginalAndAlternateRecordedArraysWithoutConversion() throws Exception {
        JSONObject index=read("ir/mi_stb/index.json");JSONArray profiles=index.getJSONArray("profiles");int pairedProfiles=0,pairs=0;
        for(int i=0;i<profiles.length();i++) {
            JSONObject metadata=profiles.getJSONObject(i),commands=read(metadata.getString("asset")).getJSONObject("commands");
            java.util.Iterator<String> keys=commands.keys();List<String> paired=new ArrayList<>();
            while(keys.hasNext()){String key=keys.next();if(!key.endsWith("_r")&&commands.has(key+"_r"))paired.add(key);}
            if(paired.isEmpty())continue;pairedProfiles++;
            for(String key:paired) {
                RecordedSignalSender sender=new RecordedSignalSender();Capture capture=new Capture();String id=metadata.getString("id");
                sender.send(id,key,commands,capture);sender.send(id,key,commands,capture);sender.send(id,key+"_r",commands,capture);
                capture.matches(0,commands.getJSONObject(key));capture.matches(1,commands.getJSONObject(key+"_r"));capture.matches(2,commands.getJSONObject(key+"_r"));
                assertEquals(3,capture.patterns.size());pairs++;
            }
        }
        assertEquals(2,pairedProfiles);assertEquals(68,pairs);
    }
}
