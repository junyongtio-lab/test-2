package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independently implemented protocol candidates, pinned to four Mi source records.
 * Protocol layouts cross-checked with IRremoteESP8266. Never evaluates source hooks.
 * A protocol match is not evidence that a particular appliance responds.
 */
final class FamilyAcEncoder {
    static final String[] MODELS={"22_3577","kk_3_123_12255","kk_3_58_4871","kk_3_125_4745"};
    private static final String[] FAMILIES={"LG28","Mitsubishi144","Daikin160","SamsungAC"};
    private static final String[] TEMPLATES={"0488001506","1223CB260100201800364000000000000000C3","1411DA27F00D000F11DA2700D331F100001C0A081F","0E02920F000000F001F2AE71A011F0"};
    private final Map<String,JSONObject> profiles=new LinkedHashMap<>();
    FamilyAcEncoder(AcSourceDatabase database) throws Exception {
        for(JSONObject metadata:database.profiles(null)) {
            int family=family(metadata.getString("model"));if(family<0)continue;
            JSONObject record=database.readRecord(metadata.getString("id"));
            JSONObject d=record.getJSONObject("definition");
            if(!d.getString("1002").equals(TEMPLATES[family]))throw new IllegalArgumentException("AC template differs from the reviewed source.");
            JSONObject p=new JSONObject(metadata.toString());
            p.put("id","family_"+metadata.getString("model")).put("family",FAMILIES[family]).put("definition",d).put("powerToggle",false)
                .put("supportsFan",true).put("supportsSwing",false).put("modes",new JSONArray().put("cool").put("heat"));
            profiles.put(metadata.getString("model"),p);
        }
        if(profiles.size()!=4)throw new IllegalArgumentException("Protocol candidate source records are missing.");
        for(JSONObject p:ExpandedAcEncoder.profiles(database))profiles.put(p.getString("id").substring(7),p);
    }
    static int family(String model){for(int i=0;i<MODELS.length;i++)if(MODELS[i].equals(model))return i;return -1;}
    static boolean supports(String model){return family(model)>=0||ExpandedAcEncoder.supports(model);}
    JSONObject find(String model){return profiles.get(model.startsWith("family_")?model.substring(7):model);}
    List<JSONObject> profiles(){return new ArrayList<>(profiles.values());}
    static PanasonicAcEncoder.Signal encode(JSONObject p,PanasonicAcEncoder.State s,boolean powerPress) throws Exception {
        if(p.optBoolean("expanded"))return ExpandedAcEncoder.encode(p,s);
        int family=family(p.getString("model"));
        if(family<0||s==null||s.mode==null||!(s.mode.equals("cool")||s.mode.equals("heat"))||s.temperature<16||s.temperature>30||s.fan<0||s.fan>3||s.swing!=-1)
            throw new IllegalArgumentException("Unsupported AC protocol candidate or setting.");
        JSONObject d=p.getJSONObject("definition");
        if(!d.getString("1002").equals(TEMPLATES[family]))throw new IllegalArgumentException("AC template differs.");
        byte[] template=PanasonicAcEncoder.hex(TEMPLATES[family]);
        byte[] b=java.util.Arrays.copyOfRange(template,1,template.length);
        boolean heat=s.mode.equals("heat");
        if(family==0) {
            // LG's 28-bit command: signature, command/mode, temperature/fan, checksum nibble.
            b[1]=(byte)(heat?4:0);b[2]=(byte)(((s.temperature-15)<<4)|new int[]{5,0,2,4}[s.fan]);
            if(!s.power){b[1]=(byte)0xc0;b[2]=5;}
            b[3]=(byte)(((b[1]&15)+((b[1]&255)>>>4)+(b[2]&15)+((b[2]&255)>>>4))&15);
        }else if(family==1) {
            b[5]=(byte)((b[5]&~0x20)|(s.power?0x20:0));
            b[6]=(byte)((b[6]&~0x38)|(heat?8:24));b[8]=(byte)(heat?0x30:0x36);
            // Mitsubishi144 encodes whole degrees as an offset from 16 C.
            b[7]=(byte)(s.temperature-16);
            b[9]=(byte)((b[9]&0x78)|(s.fan==0?0x80:new int[]{0,1,2,4}[s.fan]));
            b[17]=(byte)sum(b,0,17);
        }else if(family==2) {
            b[12]=(byte)((b[12]&0x8e)|(s.power?1:0)|(heat?0x40:0x30));
            b[16]=(byte)((b[16]&0x81)|((s.temperature-10)<<1));
            b[17]=(byte)((b[17]&0xf0)|new int[]{10,3,5,7}[s.fan]);
            b[6]=(byte)sum(b,0,6);b[19]=(byte)sum(b,7,19);
        }else {
            b[11]=(byte)((b[11]&15)|((s.temperature-16)<<4));
            b[12]=(byte)((b[12]&0x81)|(heat?0x40:0x10)|(new int[]{0,2,4,5}[s.fan]<<1));
            b[6]=(byte)((b[6]&0xcf)|(s.power?0x30:0));b[13]=(byte)((b[13]&0xcf)|(s.power?0x30:0));
            // Send complete extended state every time, so power never depends on an assumed prior state.
            byte[] extended=new byte[21];System.arraycopy(b,0,extended,0,7);
            byte[] middle=PanasonicAcEncoder.hex("01D20F00000000");System.arraycopy(middle,0,extended,7,7);
            System.arraycopy(b,7,extended,14,7);b=extended;
            for(int section=0;section<21;section+=7)samsungChecksum(b,section);
        }
        int[] pattern=waveform(d,b,family);
        int carrier=p.getInt("carrierHz");IrCatalog.validateSignal(carrier,pattern);
        return new PanasonicAcEncoder.Signal(carrier,pattern,b);
    }
    private static int sum(byte[] b,int from,int to){int n=0;for(int i=from;i<to;i++)n+=b[i]&255;return n&255;}
    private static void samsungChecksum(byte[] b,int start) {
        int count=Integer.bitCount(b[start]&255)+Integer.bitCount(b[start+1]&15)+Integer.bitCount((b[start+2]&255)>>>4);
        for(int i=start+3;i<start+7;i++)count+=Integer.bitCount(b[i]&255);
        int checksum=(~count)&255;
        b[start+1]=(byte)((b[start+1]&15)|((checksum&15)<<4));
        b[start+2]=(byte)((b[start+2]&0xf0)|(checksum>>>4));
    }
    private static void timings(List<Integer> out,String value) {
        for(String t:value.split(",")){int n=Integer.parseInt(t);if(n<1||n>1000000)throw new IllegalArgumentException("Invalid AC duration.");out.add(n);}
    }
    private static int[] waveform(JSONObject d,byte[] b,int family) throws Exception {
        List<Integer> out=new ArrayList<>();timings(out,d.getString("300"));
        boolean lsb=family!=0;
        for(int i=0;i<b.length;i++) {
            int bits=family==0&&i==b.length-1?4:8;
            for(int j=0;j<bits;j++) {
                int shift=lsb?j:bits-1-j;timings(out,d.getString(((b[i]>>>shift)&1)==0?"301":"302"));
            }
            if(family==2&&i==6)timings(out,"350,28000,4900,2000");
            if(family==3&&(i==6||i==13))timings(out,"500,3000,3000,9000");
        }
        if(family==0)timings(out,"508,40000");
        else if(family==1)timings(out,"440,15642");
        else if(family==2)timings(out,"350,29650");
        else timings(out,"500,100000");
        // The standard Mitsubishi144 transmission repeats the complete message once.
        if(family==1){List<Integer> copy=new ArrayList<>(out);out.addAll(copy);}
        int[] result=new int[out.size()];for(int i=0;i<result.length;i++)result[i]=out.get(i);return result;
    }
}
