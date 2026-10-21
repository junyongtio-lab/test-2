package com.universalremote.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent Panasonic216 encoder using bounded, non-executable Mi parameters. */
final class PanasonicAcEncoder {
    private static final String SOURCE_SHA256="9563b41eb2a52e325caa36022fd830d2c76b964690ea87cddfaf122867fc4892";
    private static final String[] MODELS={"137_2587","kk_3_137_4646","kk_3_137_7406","kk_3_137_7790","kk_3_137_7791","kk_3_137_7793"};
    private final Map<String,JSONObject> profiles=new LinkedHashMap<>();

    PanasonicAcEncoder(Context context) throws Exception { this(readIndex(context)); }
    PanasonicAcEncoder(JSONObject index) throws Exception {
        if(index.getDouble("schemaVersion")!=1 || !index.getString("kind").equals("panasonic216-parameters")
            || !index.getString("sourceParameterSha256").equals(SOURCE_SHA256)
            || !Boolean.FALSE.equals(index.get("scriptsExecuted"))) throw new IllegalArgumentException("Invalid Panasonic parameter catalogue.");
        JSONArray array=index.getJSONArray("profiles");
        if(array.length()!=MODELS.length)throw new IllegalArgumentException("Panasonic parameter profiles are missing.");
        for(int i=0;i<array.length();i++) {
            JSONObject profile=array.getJSONObject(i);validateProfile(profile);
            if(!profile.getString("model").equals(MODELS[i]) || profiles.put(profile.getString("id"),profile)!=null)
                throw new IllegalArgumentException("Panasonic source model differs.");
        }
    }
    private static JSONObject readIndex(Context context) throws Exception {
        try(InputStream in=context.getAssets().open("ir/panasonic_ac/index.json")) {
            return new JSONObject(IrCatalog.readBounded(in,64*1024));
        }
    }
    List<JSONObject> profiles(){return new ArrayList<>(profiles.values());}
    JSONObject find(String id){return profiles.get(id);}

    static final class State {
        boolean power; String mode; int temperature,fan,swing;
        State(boolean power,String mode,int temperature,int fan,int swing) {
            this.power=power;this.mode=mode;this.temperature=temperature;this.fan=fan;this.swing=swing;
        }
        State copy(){return new State(power,mode,temperature,fan,swing);}
        JSONObject toJson() throws Exception {
            return new JSONObject().put("power",power).put("mode",mode).put("temperature",temperature).put("fan",fan).put("swing",swing);
        }
        static State fromJson(JSONObject value) throws Exception {
            if(!(value.get("power") instanceof Boolean))throw new IllegalArgumentException("Invalid AC power state.");
            State state=new State(value.getBoolean("power"),value.getString("mode"),integer(value,"temperature"),integer(value,"fan"),integer(value,"swing"));
            checkState(state);return state;
        }
    }
    static State defaults(JSONObject profile) {
        return new State(true,"cool",24,profile.optBoolean("supportsFan")?0:-1,profile.optBoolean("supportsSwing")?0:-1);
    }
    static final class Signal {
        final int carrierHz; final int[] pattern; final byte[] packet;
        Signal(int carrierHz,int[] pattern,byte[] packet){this.carrierHz=carrierHz;this.pattern=pattern;this.packet=packet;}
    }
    static Signal encode(JSONObject profile,State state,boolean powerButtonPress) throws Exception {
        validateProfile(profile);checkState(state);
        JSONObject modes=profile.getJSONObject("modePatches");
        if(!modes.has(state.mode))throw new IllegalArgumentException("This source profile does not support that mode.");
        if(!profile.getBoolean("supportsFan") && state.fan!=-1)throw new IllegalArgumentException("This source profile has no fan-speed mapping.");
        if(!profile.getBoolean("supportsSwing") && state.swing!=-1)throw new IllegalArgumentException("This source profile has no swing mapping.");
        byte[] packet=hex(profile.getString("templateHex"));
        applyBytes(packet,modes.getJSONArray(state.mode));
        applyBytes(packet,profile.getJSONObject("temperaturePatches").getJSONArray(Integer.toString(state.temperature)));
        if(state.fan>=0)applyBytes(packet,profile.getJSONArray("fanPatches").getJSONArray(state.fan));
        if(state.swing>=0)applyBits(packet,profile.getJSONArray("swingPatches").getJSONArray(state.swing));
        if(profile.getBoolean("powerToggle"))packet[13]=(byte)((packet[13]&0xfe)|(powerButtonPress?1:0));
        else packet[13]=(byte)((packet[13]&0xfe)|(state.power?1:0));
        packet[7]=(byte)sum(packet,0,7);packet[26]=(byte)sum(packet,8,26);
        int[] pattern=pattern(profile,packet,8);
        int carrier=integer(profile,"carrierHz");IrCatalog.validateSignal(carrier,pattern);
        return new Signal(carrier,pattern,packet);
    }
    private static void checkState(State state) {
        if(state==null || state.mode==null || !(state.mode.equals("cool")||state.mode.equals("heat")||state.mode.equals("dry")||state.mode.equals("fan"))
            || state.temperature<16 || state.temperature>30 || state.fan< -1 || state.fan>3 || state.swing< -1 || state.swing>5)
            throw new IllegalArgumentException("Unsupported Panasonic AC setting.");
    }
    private static int integer(JSONObject object,String key) throws Exception {
        if(!(object.get(key) instanceof Number))throw new IllegalArgumentException("Expected an integer Panasonic parameter.");
        int value=object.getInt(key);
        if(object.getDouble(key)!=value)throw new IllegalArgumentException("Expected an integer Panasonic parameter.");
        return value;
    }
    private static int number(JSONArray array,int index) throws Exception {
        if(!(array.get(index) instanceof Number))throw new IllegalArgumentException("Expected an integer Panasonic parameter.");
        int value=array.getInt(index);
        if(array.getDouble(index)!=value)throw new IllegalArgumentException("Expected an integer Panasonic parameter.");
        return value;
    }
    private static int[] timings(JSONObject profile,String key,int size) throws Exception {
        JSONArray array=profile.getJSONArray(key);
        if(array.length()!=size)throw new IllegalArgumentException("Invalid Panasonic framing.");
        int[] result=new int[size];
        for(int i=0;i<size;i++){result[i]=number(array,i);if(result[i]<1||result[i]>1000000)throw new IllegalArgumentException("Invalid Panasonic timing.");}
        return result;
    }
    private static void validateProfile(JSONObject p) throws Exception {
        String model=p.getString("model");boolean known=false;for(String candidate:MODELS)if(candidate.equals(model))known=true;
        if(!known || !p.getString("id").equals("pana_"+model) || !p.getString("brand").equals("Panasonic")
            || !p.getString("category").equals("air_conditioner") || !Boolean.FALSE.equals(p.get("hardwareTested"))
            || !(p.get("powerToggle") instanceof Boolean) || p.getBoolean("powerToggle")!=model.equals("kk_3_137_7793"))
            throw new IllegalArgumentException("Invalid Panasonic source identity.");
        int carrier=integer(p,"carrierHz");if(carrier<20000||carrier>60000)throw new IllegalArgumentException("Invalid Panasonic carrier.");
        byte[] packet=hex(p.getString("templateHex"));
        if(packet.length!=27 || packet[0]!=2 || packet[1]!=0x20 || (packet[2]&255)!=0xe0 || packet[8]!=2 || packet[9]!=0x20 || (packet[10]&255)!=0xe0)
            throw new IllegalArgumentException("Unsupported Panasonic packet.");
        timings(p,"lead",2);timings(p,"zero",2);timings(p,"one",2);timings(p,"between",4);
        if(integer(p,"trailingGap")!=1000)throw new IllegalArgumentException("Invalid Panasonic trailing gap.");
        JSONObject modes=p.getJSONObject("modePatches");JSONArray labels=p.getJSONArray("modes");
        if(labels.length()!=modes.length() || modes.length()<3 || modes.length()>4)throw new IllegalArgumentException("Invalid Panasonic modes.");
        java.util.Set<String> seenModes=new java.util.HashSet<>();
        for(int i=0;i<labels.length();i++) {
            String mode=labels.getString(i);
            if(!seenModes.add(mode) || !(mode.equals("cool")||mode.equals("heat")||mode.equals("dry")||mode.equals("fan")))throw new IllegalArgumentException("Unsupported Panasonic mode.");
            checkBytePatches(modes.getJSONArray(mode),13,14);
        }
        JSONObject temps=p.getJSONObject("temperaturePatches");
        if(temps.length()!=15)throw new IllegalArgumentException("Invalid Panasonic temperature table.");
        for(int t=16;t<=30;t++)checkBytePatches(temps.getJSONArray(Integer.toString(t)),14,14);
        JSONArray fans=p.getJSONArray("fanPatches"),swings=p.getJSONArray("swingPatches");
        if(!(p.get("supportsFan") instanceof Boolean) || !(p.get("supportsSwing") instanceof Boolean)
            || fans.length()!=(p.getBoolean("supportsFan")?4:0) || swings.length()!=(p.getBoolean("supportsSwing")?6:0))throw new IllegalArgumentException("Invalid Panasonic controls.");
        for(int i=0;i<fans.length();i++)checkBytePatches(fans.getJSONArray(i),16,16);
        for(int i=0;i<swings.length();i++) {
            JSONArray patches=swings.getJSONArray(i);
            if(patches.length()<1 || patches.length()>2)throw new IllegalArgumentException("Invalid Panasonic swing patches.");
            for(int j=0;j<patches.length();j++) {
                JSONArray patch=patches.getJSONArray(j);if(patch.length()!=3)throw new IllegalArgumentException("Invalid swing patch.");
                int start=number(patch,0),end=number(patch,1),value=number(patch,2);
                if(start<128||end>144||start>=end||end-start>8||value<0||value>=(1<<(end-start)))throw new IllegalArgumentException("Invalid swing bit range.");
            }
        }
        JSONObject source=p.getJSONObject("source");
        if(!source.getString("sourceSha256").matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Missing Panasonic source hash.");
        String kind=source.getString("kind");
        boolean apk=model.equals(MODELS[0])||model.equals(MODELS[1])||model.equals(MODELS[2]);
        if(apk) {
            if(!kind.equals("mi724-apk-local-database") || !source.getString("rowId").equals(model)
                || !source.getString("table").equals("kk_remote_ircode")
                || !source.getString("parameterTable").equals("kk_remote_ircode_src")
                || !source.getString("apkSha256").equals("4c20655f2b636d86152202994da2a1b88f4ebf5bddf58b7ef6c2bf8e42fa5c73")
                || !source.getString("databaseSha256").equals("727a271f25889d97fc94b021b240f62d1ce559ce8a98cf14b7ac0d3efb657bfa"))
                throw new IllegalArgumentException("Invalid uploaded-APK Panasonic provenance.");
        } else if(!kind.equals("mi-github-v2.1")
            || !source.getString("archiveSha256").equals("44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895")
            || !source.getString("sourceFile").equals("database_dump/3_AC/models/"+model+".json"))
            throw new IllegalArgumentException("Invalid GitHub Panasonic provenance.");
    }
    private static void checkBytePatches(JSONArray patches,int min,int max) throws Exception {
        if(patches.length()<1 || patches.length()>2)throw new IllegalArgumentException("Invalid Panasonic byte mapping.");
        for(int i=0;i<patches.length();i++) {
            JSONArray patch=patches.getJSONArray(i);if(patch.length()!=2)throw new IllegalArgumentException("Invalid Panasonic byte patch.");
            int index=number(patch,0),value=number(patch,1);
            if(index<min||index>max||value<0||value>255)throw new IllegalArgumentException("Unsafe Panasonic byte patch.");
        }
    }
    private static void applyBytes(byte[] bytes,JSONArray patches) throws Exception {
        for(int i=0;i<patches.length();i++){JSONArray patch=patches.getJSONArray(i);bytes[number(patch,0)]=(byte)number(patch,1);}
    }
    private static void applyBits(byte[] bytes,JSONArray patches) throws Exception {
        for(int i=0;i<patches.length();i++) {
            JSONArray patch=patches.getJSONArray(i);int start=number(patch,0),end=number(patch,1),value=number(patch,2);
            for(int bit=start;bit<end;bit++) {
                int mask=1<<(7-bit%8),set=(value>>(end-1-bit))&1,index=bit/8;
                bytes[index]=(byte)((bytes[index]&~mask)|(set==1?mask:0));
            }
        }
    }
    static int[] pattern(JSONObject profile,byte[] bytes,int split) throws Exception {
        if(bytes.length<2||bytes.length>27||split<1||split>=bytes.length)throw new IllegalArgumentException("Invalid Panasonic frame split.");
        int[] lead=timings(profile,"lead",2),zero=timings(profile,"zero",2),one=timings(profile,"one",2),between=timings(profile,"between",4);
        List<Integer> result=new ArrayList<>();for(int v:lead)result.add(v);
        for(int i=0;i<bytes.length;i++) {
            for(int bit=0;bit<8;bit++){int[] pair=((bytes[i]>>bit)&1)==0?zero:one;result.add(pair[0]);result.add(pair[1]);}
            if(i==split-1)for(int v:between)result.add(v);
        }
        result.add(one[0]);result.add(integer(profile,"trailingGap"));
        int[] pattern=new int[result.size()];for(int i=0;i<pattern.length;i++)pattern[i]=result.get(i);
        return pattern;
    }
    static byte[] hex(String value) {
        if(value==null || value.length()%2!=0 || !value.matches("[a-fA-F0-9]+"))throw new IllegalArgumentException("Invalid Panasonic packet bytes.");
        byte[] result=new byte[value.length()/2];for(int i=0;i<result.length;i++)result[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);
        return result;
    }
    private static int sum(byte[] packet,int start,int end){int sum=0;for(int i=start;i<end;i++)sum+=packet[i]&255;return sum&255;}
}
