package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Iterator;

/** Bounded source-table controls for the hash-pinned AC112 allowlist. No hook execution. */
final class Ac112Controls {
    private static final String TIMER_SLEEP="543afbc395881cc6ac8e9b1f1dfa89b73fd18376de8875432752d5274670bc4f";
    private static final String TIMER_SLEEP_NO_INDICATOR="216038662958f8bc1d58d6c12732a831c9d056b6bc1ceb3a9cb73869b13fffa2";
    private static final String TIMER_ONLY="a7cb87600865c3018da55ef44be1501c54a5b2afb5d908fc20238fcd8c65bbef";
    private static int hook(JSONObject d) throws Exception {
        if(!d.has("1518"))return 0;
        String hash=IrCatalog.sha256(d.getString("1518"));
        if(hash.equals(TIMER_SLEEP))return 1;
        if(hash.equals(TIMER_SLEEP_NO_INDICATOR))return 2;
        if(hash.equals(TIMER_ONLY))return 3;
        throw new IllegalArgumentException("Unreviewed AC112 hook; it will not be executed.");
    }
    private static JSONArray patches(String encoded,int start,int end) throws Exception {
        byte[] raw=PanasonicAcEncoder.hex(encoded);
        if(raw.length==0||raw.length%4!=0||raw.length>28)throw new IllegalArgumentException("Invalid AC112 control table.");
        JSONArray result=new JSONArray();
        for(int i=0;i<raw.length;i+=4){
            int from=raw[i+1]&255,to=raw[i+2]&255,value=raw[i+3]&255;
            if(raw[i]!=3||from<start||to>end||from>=to||to-from>5||value>=(1<<(to-from)))throw new IllegalArgumentException("Unsafe AC112 field.");
            result.put(new JSONArray().put(from).put(to).put(value));
        }
        return result;
    }
    private static String name(String fid) {
        switch(fid){case "8":return "Turbo";case "12":return "Auxiliary heat";case "13":return "Health / filter";
            case "18":return "Energy saver";case "21":return "Horizontal swing";case "22":return "Sleep";
            case "26":return "I Feel / body sensor";case "28":return "Eco";case "34":return "Display light";
            default:throw new IllegalArgumentException("Unreviewed AC112 special function.");}
    }
    private static String modes(JSONObject d,String fid) {
        for(String entry:d.optString("1515").split("@")){
            String[] fields=entry.split("\\|");if(fields.length==4&&fields[0].equals(fid))return fields[3];
        }
        return "";
    }
    static JSONObject describe(JSONObject d) throws Exception {
        JSONArray fans=patches(d.getString("1013"),69,72),swings=patches(d.getString("1015"),64,69);
        if(!d.getString("1013").equals("03454800034548020345480303454805"))throw new IllegalArgumentException("Unreviewed AC112 fan table.");
        JSONArray swingLabels=new JSONArray();
        String[] labels={"Off / fixed","Highest","High","Middle","Low","Lowest","Unsupported","Swing on"};
        for(int i=0;i<swings.length();i++){
            int value=swings.getJSONArray(i).getInt(2);if(value==6||value>7)throw new IllegalArgumentException("Unreviewed swing value.");
            swingLabels.put(labels[value]);
        }
        JSONObject extras=new JSONObject();
        String encoded=d.optString("1017");
        if(!encoded.isEmpty())for(String entry:encoded.split("@")){
            byte[] p=PanasonicAcEncoder.hex(entry);
            if(p.length!=6||p[0]!=5)throw new IllegalArgumentException("Invalid AC112 special table.");
            String fid=Integer.toString(p[1]&255);String label=name(fid);int option=p[2]&255,from=p[3]&255,to=p[4]&255,value=p[5]&255;
            if(from<40||to>104||from>=to||to-from>4||value>=(1<<(to-from))||option>7)throw new IllegalArgumentException("Unsafe AC112 special field.");
            if(modes(d,fid).isEmpty())throw new IllegalArgumentException("Missing special-function mode limits.");
            JSONObject feature=extras.optJSONObject(fid);
            if(feature==null){feature=new JSONObject().put("label",label).put("modes",modes(d,fid)).put("options",new JSONArray());extras.put(fid,feature);}
            feature.getJSONArray("options").put(new JSONObject().put("value",option).put("label",fid.equals("21")?"Source option "+option:option==0?"Off":"On")
                .put("patch",new JSONArray().put(from).put(to).put(value)));
        }
        int kind=hook(d);
        if((kind==1||kind==2)&&!extras.has("22"))extras.put("22",new JSONObject().put("label","Sleep / night fan").put("modes",modes(d,"22")).put("nightFan",true)
            .put("options",new JSONArray().put(new JSONObject().put("value",0).put("label","Off")).put(new JSONObject().put("value",1).put("label","On"))));
        if((kind==1||kind==2)&&extras.has("22"))extras.getJSONObject("22").put("nightFan",true);
        JSONObject result=new JSONObject().put("fanPatches",fans).put("swingPatches",swings).put("swingLabels",swingLabels).put("extras",extras).put("timerHook",kind);
        if(kind!=0){
            int min=0,max=0,step=0;
            for(String entry:d.getString("1515").split("@")){
                String[] f=entry.split("\\|");if(f.length==4&&(f[0].equals("9")||f[0].equals("10"))){
                    String[] bounds=f[1].split("[-,]");if(bounds.length!=3)throw new IllegalArgumentException("Invalid AC112 timer bounds.");
                    int a=Integer.parseInt(bounds[0]),b=Integer.parseInt(bounds[1]),c=Integer.parseInt(bounds[2]);
                    if(min!=0&&(a!=min||b!=max||c!=step))throw new IllegalArgumentException("Timer bounds differ.");min=a;max=b;step=c;
                }
            }
            if(!((min==10&&max==1440&&step==10)||(min==60&&max==480&&step==60)||(min==0&&max==1440&&step==30)))throw new IllegalArgumentException("Unreviewed timer range.");
            result.put("timerMin",min).put("timerMax",max).put("timerStep",step);
        }
        return result;
    }
    static PanasonicAcEncoder.State persistentRequest(PanasonicAcEncoder.State request,PanasonicAcEncoder.State previous) {
        PanasonicAcEncoder.State saved=request.copy();
        if(saved.extras.has("9")||saved.extras.has("10")){saved.extras.remove("9");saved.extras.remove("10");saved.power=previous.power;}
        return saved;
    }
    private static void apply(byte[] b,JSONArray patch) throws Exception {
        int from=patch.getInt(0),to=patch.getInt(1),value=patch.getInt(2);
        for(int bit=from;bit<to;bit++){int mask=1<<(7-bit%8);b[bit/8]=(byte)((b[bit/8]&~mask)|(((value>>(to-1-bit))&1)==0?0:mask));}
    }
    static void apply(JSONObject d,PanasonicAcEncoder.State state,byte[] packet) throws Exception {
        JSONObject c=describe(d);JSONArray fans=c.getJSONArray("fanPatches"),swings=c.getJSONArray("swingPatches");
        if(state.fan< -1||state.fan>=fans.length()||state.swing< -1||state.swing>=swings.length()||state.extras.length()>12)throw new IllegalArgumentException("Unsupported AC112 fan or swing setting.");
        if(state.fan>=0)apply(packet,fans.getJSONArray(state.fan));
        if(state.swing>=0)apply(packet,swings.getJSONArray(state.swing));
        JSONObject supported=c.getJSONObject("extras");Iterator<String> keys=state.extras.keys();
        int timerId=0,timerMinutes=0;
        while(keys.hasNext()){
            String fid=keys.next();Object raw=state.extras.get(fid);
            if(!(raw instanceof Number)||state.extras.getDouble(fid)!=state.extras.getInt(fid))throw new IllegalArgumentException("AC112 functions require integer options.");
            int value=state.extras.getInt(fid);
            if(fid.equals("9")||fid.equals("10")){
                if(timerId!=0||c.getInt("timerHook")==0||value<0||value>c.getInt("timerMax")||(value!=0&&(value<c.getInt("timerMin")||value%c.getInt("timerStep")!=0)))throw new IllegalArgumentException("Unsupported AC112 timer request.");
                timerId=Integer.parseInt(fid);timerMinutes=value;continue;
            }
            JSONObject feature=supported.optJSONObject(fid);if(feature==null)throw new IllegalArgumentException("This definition has no mapping for that special function.");
            if(value!=0&&!feature.getString("modes").contains(state.mode.equals("heat")?"H":"C"))throw new IllegalArgumentException(feature.getString("label")+" is unavailable in this mode.");
            JSONArray options=feature.getJSONArray("options");JSONObject found=null;
            for(int i=0;i<options.length();i++)if(options.getJSONObject(i).getInt("value")==value)found=options.getJSONObject(i);
            if(found==null)throw new IllegalArgumentException("Unsupported AC112 function option.");
            if(found.has("patch"))apply(packet,found.getJSONArray("patch"));
            if(feature.optBoolean("nightFan")&&value==1)packet[8]=(byte)((packet[8]&0xf8)|1);
        }
        if(timerId!=0){
            // Independent translation of three exact, hash-reviewed source hooks. Never executes source text.
            packet[5]=(byte)((packet[5]&255)&~0x18);packet[9]=0;packet[10]=0;
            if(c.getInt("timerHook")!=2)packet[8]=(byte)(packet[8]&~0x40);
            if(timerMinutes>0){
                packet[5]=(byte)(timerId==9?0x34:0x2c);
                packet[timerId==9?10:9]=(byte)(timerMinutes/10);
                if(c.getInt("timerHook")!=2)packet[8]=(byte)(packet[8]|0x40);
            }
        }
    }
}
