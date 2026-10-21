package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Checks actual bundled parameters against an independent packet and public IR framing. */
public final class PanasonicAcEncoderTest {
    private static final String[] MODELS={"137_2587","kk_3_137_4646","kk_3_137_7406","kk_3_137_7790","kk_3_137_7791","kk_3_137_7793"};
    private JSONObject index() throws Exception {
        try(FileInputStream in=new FileInputStream("src/main/assets/ir/panasonic_ac/index.json")) {
            return new JSONObject(IrCatalog.readBounded(in,64*1024));
        }
    }
    private List<JSONObject> profiles() throws Exception {return new PanasonicAcEncoder(index()).profiles();}
    private interface Operation {void run() throws Exception;}
    private void rejects(Operation operation) throws Exception {
        try{operation.run();fail("Invalid Panasonic data reached encoding");}
        catch(IllegalArgumentException|org.json.JSONException expected){/* No signal was returned. */}
    }
    private int checksum(byte[] packet,int first,int last) {
        int sum=0;for(int i=first;i<last;i++)sum+=packet[i]&255;return sum&255;
    }
    private String hex(byte[] value) {
        StringBuilder result=new StringBuilder();for(byte b:value)result.append(String.format("%02x",b&255));return result.toString();
    }
    @Test public void allSixProfilesKeepActualSourceIdentityAndAvailableControls() throws Exception {
        List<JSONObject> profiles=profiles();assertEquals(6,profiles.size());
        for(int i=0;i<profiles.size();i++) {
            JSONObject p=profiles.get(i);assertEquals(MODELS[i],p.getString("model"));
            assertEquals("pana_"+MODELS[i],p.getString("id"));assertFalse(p.getBoolean("hardwareTested"));
            assertEquals(i<3?"mi724-apk-local-database":"mi-github-v2.1",p.getJSONObject("source").getString("kind"));
            assertEquals(i<3,p.getBoolean("supportsFan"));assertTrue(p.getBoolean("supportsSwing"));
            assertEquals(i==5,p.getBoolean("powerToggle"));assertFalse(p.getJSONObject("modePatches").has("auto"));
            assertEquals(i==0||i==5,p.getJSONObject("modePatches").has("fan"));
        }
        assertEquals(37900,profiles.get(1).getInt("carrierHz"));
    }
    @Test public void publishedPanasonic216OffCool25GoldenPacketMatchesExactly() throws Exception {
        // Independent wire packet published in IRremoteESP8266 issue #525.
        // This literal is not generated from the Mi parameter tables or encoder.
        JSONObject profile=profiles().get(0);
        PanasonicAcEncoder.State state=new PanasonicAcEncoder.State(false,"cool",25,0,0);
        PanasonicAcEncoder.Signal result=PanasonicAcEncoder.encode(profile,state,false);
        assertEquals("0220e004000000060220e00400303280af00000660000080000683",hex(result.packet));
        assertEquals(38000,result.carrierHz);assertEquals(0x83,result.packet[26]&255);
    }
    @Test public void waveformPreservesCompleteTwoFramesLsbOrderAndSourceTimingUnits() throws Exception {
        JSONObject profile=profiles().get(0);
        PanasonicAcEncoder.Signal signal=PanasonicAcEncoder.encode(profile,new PanasonicAcEncoder.State(false,"cool",25,0,0),false);
        assertEquals(440,signal.pattern.length);
        assertArrayEquals(new int[]{3490,1740,411,474,411,1250,411,474},Arrays.copyOf(signal.pattern,8));
        assertArrayEquals(new int[]{411,10010,3490,1740},Arrays.copyOfRange(signal.pattern,130,134));
        assertArrayEquals(new int[]{411,1000},Arrays.copyOfRange(signal.pattern,438,440));
        byte[] decoded=new byte[27];int cursor=2;
        for(int byteIndex=0;byteIndex<27;byteIndex++) {
            for(int bit=0;bit<8;bit++) {
                assertEquals(411,signal.pattern[cursor++]);int space=signal.pattern[cursor++];
                assertTrue(space==474||space==1250);if(space==1250)decoded[byteIndex]|=1<<bit;
            }
            if(byteIndex==7)cursor+=4;
        }
        assertEquals(438,cursor);assertArrayEquals(signal.packet,decoded);
    }
    @Test public void allMappedModesAndTemperaturesProduceBoundedChecksummedPackets() throws Exception {
        for(JSONObject profile:profiles()) {
            JSONArray modes=profile.getJSONArray("modes");
            for(int m=0;m<modes.length();m++)for(int temperature=16;temperature<=30;temperature++) {
                PanasonicAcEncoder.State state=PanasonicAcEncoder.defaults(profile);state.mode=modes.getString(m);state.temperature=temperature;
                PanasonicAcEncoder.Signal signal=PanasonicAcEncoder.encode(profile,state,false);
                assertEquals(27,signal.packet.length);assertEquals(temperature*2,signal.packet[14]&255);
                assertEquals(checksum(signal.packet,0,7),signal.packet[7]&255);
                assertEquals(checksum(signal.packet,8,26),signal.packet[26]&255);
                assertEquals(profile.getInt("carrierHz"),signal.carrierHz);IrCatalog.validateSignal(signal.carrierHz,signal.pattern);
            }
        }
    }
    @Test public void explicitPowerChangesOnlyRecordedPowerBitAndChecksum() throws Exception {
        for(JSONObject profile:profiles().subList(0,5)) {
            PanasonicAcEncoder.State state=PanasonicAcEncoder.defaults(profile);
            state.power=true;byte[] on=PanasonicAcEncoder.encode(profile,state,true).packet;
            state.power=false;byte[] off=PanasonicAcEncoder.encode(profile,state,true).packet;
            assertEquals(1,on[13]&1);assertEquals(0,off[13]&1);
            assertEquals(1,(on[13]&255)-(off[13]&255));
            for(int i=0;i<27;i++)if(i!=13&&i!=26)assertEquals(on[i],off[i]);
            assertEquals((on[26]-1)&255,off[26]&255);
        }
        JSONObject special=profiles().get(3);PanasonicAcEncoder.State state=PanasonicAcEncoder.defaults(special);
        assertEquals(0x39,PanasonicAcEncoder.encode(special,state,false).packet[13]&255);
    }
    @Test public void toggleFamilySetsActionOnlyForAnExplicitPowerPress() throws Exception {
        JSONObject p=profiles().get(5);PanasonicAcEncoder.State state=PanasonicAcEncoder.defaults(p);
        byte[] settings=PanasonicAcEncoder.encode(p,state,false).packet;
        byte[] press=PanasonicAcEncoder.encode(p,state,true).packet;
        assertEquals(0,settings[13]&1);assertEquals(1,press[13]&1);
        state.power=false;assertArrayEquals(settings,PanasonicAcEncoder.encode(p,state,false).packet);
        assertArrayEquals(press,PanasonicAcEncoder.encode(p,state,true).packet);
        assertEquals("0220e0080000000a",hex(Arrays.copyOf(press,8)));
    }
    @Test public void fanAndSwingMappingsKeepTheirIndependentBitsAndModelSpecificSwingFlag() throws Exception {
        int[] speedNibbles={0xa0,0x30,0x50,0x70};int[] swingNibbles={15,1,2,3,4,5};
        for(JSONObject p:profiles()) {
            int speeds=p.getBoolean("supportsFan")?4:1;
            for(int fan=0;fan<speeds;fan++)for(int swing=0;swing<6;swing++) {
                PanasonicAcEncoder.State state=PanasonicAcEncoder.defaults(p);state.swing=swing;if(p.getBoolean("supportsFan"))state.fan=fan;
                byte[] packet=PanasonicAcEncoder.encode(p,state,false).packet;
                assertEquals(swingNibbles[swing],packet[16]&15);
                assertEquals(p.getBoolean("supportsFan")?speedNibbles[fan]:0xa0,packet[16]&0xf0);
                if(p.getString("model").equals("kk_3_137_7406"))assertEquals(swing==0?8:13,packet[17]&15);
            }
        }
    }
    @Test public void unsupportedModeAndFanControlsNeverProduceASignal() throws Exception {
        for(JSONObject p:profiles()) {
            PanasonicAcEncoder.State state=PanasonicAcEncoder.defaults(p);state.mode="auto";
            rejects(()->PanasonicAcEncoder.encode(p,state,false));
            if(!p.getJSONObject("modePatches").has("fan")) {
                state.mode="fan";rejects(()->PanasonicAcEncoder.encode(p,state,false));
            }
            if(!p.getBoolean("supportsFan")) {
                state.mode="cool";state.fan=0;rejects(()->PanasonicAcEncoder.encode(p,state,false));
            }
        }
    }
    @Test public void repeatedEncodingDoesNotMutateParametersRequestsOrPreviousSignals() throws Exception {
        for(JSONObject p:profiles()) {
            PanasonicAcEncoder.State state=PanasonicAcEncoder.defaults(p);String parameters=p.toString(),request=state.toJson().toString();
            PanasonicAcEncoder.Signal first=PanasonicAcEncoder.encode(p,state,false),second=PanasonicAcEncoder.encode(p,state,false);
            assertEquals(parameters,p.toString());assertEquals(request,state.toJson().toString());
            assertArrayEquals(first.packet,second.packet);assertArrayEquals(first.pattern,second.pattern);
            first.packet[0]=0;first.pattern[0]=1;
            PanasonicAcEncoder.Signal third=PanasonicAcEncoder.encode(p,state,false);
            assertArrayEquals(second.packet,third.packet);assertArrayEquals(second.pattern,third.pattern);
        }
    }
    @Test public void requestedStateRoundTripsWithoutSendingAndRejectsCorruptValues() throws Exception {
        JSONObject p=profiles().get(0);PanasonicAcEncoder.State state=new PanasonicAcEncoder.State(false,"dry",30,3,5);
        PanasonicAcEncoder.State restored=PanasonicAcEncoder.State.fromJson(state.toJson());
        assertEquals(state.toJson().toString(),restored.toJson().toString());assertNotSame(state,restored);
        assertArrayEquals(PanasonicAcEncoder.encode(p,state,false).packet,PanasonicAcEncoder.encode(p,restored,false).packet);
        for(String key:new String[]{"temperature","fan","swing"}) {
            JSONObject bad=state.toJson().put(key,1.5);rejects(()->PanasonicAcEncoder.State.fromJson(bad));
        }
        rejects(()->PanasonicAcEncoder.State.fromJson(state.toJson().put("temperature","24")));
        rejects(()->PanasonicAcEncoder.State.fromJson(state.toJson().put("power","true")));
        rejects(()->PanasonicAcEncoder.State.fromJson(state.toJson().put("mode","auto")));
    }
    @Test public void outOfRangeRequestsNeverProduceASignal() throws Exception {
        JSONObject p=profiles().get(0);
        rejects(()->PanasonicAcEncoder.encode(p,null,false));
        for(int t:new int[]{15,31})rejects(()->PanasonicAcEncoder.encode(p,new PanasonicAcEncoder.State(true,"cool",t,0,0),false));
        for(int f:new int[]{-2,4})rejects(()->PanasonicAcEncoder.encode(p,new PanasonicAcEncoder.State(true,"cool",24,f,0),false));
        for(int s:new int[]{-2,6})rejects(()->PanasonicAcEncoder.encode(p,new PanasonicAcEncoder.State(true,"cool",24,0,s),false));
    }
    @Test public void unsafeCataloguesAndBitPatchesAreRejectedBeforeEncoding() throws Exception {
        for(String field:new String[]{"kind","sourceParameterSha256","scriptsExecuted"}) {
            JSONObject root=index();root.put(field,field.equals("scriptsExecuted")?true:"changed");rejects(()->new PanasonicAcEncoder(root));
        }
        for(String mutation:new String[]{"id","carrier","byte","swing","mode","source"}) {
            JSONObject root=index(),p=root.getJSONArray("profiles").getJSONObject(0);
            if(mutation.equals("id"))p.put("id","pana_guessed");
            if(mutation.equals("carrier"))p.put("carrierHz",19000);
            if(mutation.equals("byte"))p.getJSONObject("temperaturePatches").put("24",new JSONArray("[[27,0]]"));
            if(mutation.equals("swing"))p.getJSONArray("swingPatches").put(0,new JSONArray("[[0,8,0]]"));
            if(mutation.equals("mode"))p.put("modes",new JSONArray("[cool,cool,dry,heat]"));
            if(mutation.equals("source"))p.getJSONObject("source").put("databaseSha256",String.format("%064d",0));
            rejects(()->new PanasonicAcEncoder(root));
        }
    }
    @Test public void encodedSignalReachesTheExistingCarrierGateExactlyOnceUnchanged() throws Exception {
        JSONObject p=profiles().get(1);PanasonicAcEncoder.Signal signal=PanasonicAcEncoder.encode(p,PanasonicAcEncoder.defaults(p),false);
        final int[] sends={0};
        IrCatalog.Emitter phone=new IrCatalog.Emitter() {
            public boolean hasEmitter(){return true;}
            public int[][] carrierRanges(){return new int[][]{{37900,37900}};}
            public void transmit(int hz,int[] timings){assertEquals(37900,hz);assertArrayEquals(signal.pattern,timings);sends[0]++;}
        };
        IrCatalog.transmitSignal(phone,signal.carrierHz,signal.pattern);assertEquals(1,sends[0]);
        IrCatalog.Emitter absent=new IrCatalog.Emitter() {
            public boolean hasEmitter(){return false;}
            public int[][] carrierRanges(){throw new AssertionError("Absent emitter queried");}
            public void transmit(int hz,int[] timings){throw new AssertionError("Absent emitter sent");}
        };
        try{IrCatalog.transmitSignal(absent,signal.carrierHz,signal.pattern);fail("Absent emitter sent");}
        catch(IllegalStateException expected){assertEquals(1,sends[0]);}
    }
}
