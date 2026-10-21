package com.universalremote.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Complete Mi APK AC source records. Reading source text never evaluates a program or sends IR. */
final class AcSourceDatabase {
    interface Reader { InputStream open(String path) throws Exception; }
    private static final String[] PANASONIC={"137_2587","kk_3_137_4646","kk_3_137_7406"};
    private static final Pattern ID=Pattern.compile("ac724_[a-f0-9]{24}"),MODEL=Pattern.compile("[A-Za-z0-9_]{1,100}"),
        BRAND=Pattern.compile("[0-9]{1,10}"),DIGEST=Pattern.compile("[a-f0-9]{64}"),RAW_ID=Pattern.compile("mi_[a-f0-9]{24}");
    private final Map<String,JSONObject> records=new LinkedHashMap<>();
    private final Reader reader;

    AcSourceDatabase(Context context) throws Exception {
        this(readIndex(context),path->context.getAssets().open(path));
    }
    private static JSONObject readIndex(Context context) throws Exception {
        try(InputStream in=context.getAssets().open("ir/mi_ac_database/index.json")) {
            return new JSONObject(IrCatalog.readBounded(in,1024*1024));
        }
    }
    AcSourceDatabase(JSONObject index,Reader reader) throws Exception {
        this.reader=reader;
        JSONObject source=index.getJSONObject("source"),coverage=index.getJSONObject("coverage");
        if(index.getDouble("schemaVersion")!=1||!index.getString("kind").equals("mi724-complete-ac-source-data")
            ||!Boolean.FALSE.equals(index.get("programsExecuted"))||!source.getString("versionName").equals("7.2.4M")
            ||source.getDouble("versionCode")!=6500||!source.getString("apkSha256").equals(IrCatalog.APK_SHA256)
            ||!source.getString("databaseSha256").equals(IrCatalog.APK_DATABASE_SHA256)
            ||!source.getString("archiveSha256").equals(IrCatalog.APK_ARCHIVE_SHA256))
            throw new IllegalArgumentException("Unrecognized complete AC source database.");
        JSONArray profiles=index.getJSONArray("profiles");int recorded=0,stateful=0,implemented=0;Set<String> models=new TreeSet<>();
        for(int i=0;i<profiles.length();i++) {
            JSONObject p=profiles.getJSONObject(i);String id=p.getString("id"),model=p.getString("model");
            String kind=p.getString("definitionKind"),control=p.getJSONObject("control").getString("kind");
            boolean raw=kind.equals("recorded"),pana=isPanasonic(model);
            if(!ID.matcher(id).matches()||!MODEL.matcher(model).matches()||!models.add(model)
                ||!BRAND.matcher(p.getString("brandId")).matches()||p.getString("brand").trim().isEmpty()
                ||p.getString("brand").length()>160||!p.getString("category").equals("air_conditioner")
                ||!p.getString("sourceTable").equals("kk_remote_ircode")||!Boolean.FALSE.equals(p.get("hardwareTested"))
                ||!p.getString("asset").equals("ir/mi_ac_database/profiles/"+id+".json")
                ||!DIGEST.matcher(p.getString("assetSha256")).matches()
                ||!DIGEST.matcher(p.getString("sourceRecordSha256")).matches()
                ||!DIGEST.matcher(p.getString("brandRecordSha256")).matches()
                ||p.getDouble("carrierHz")!=p.getInt("carrierHz")||p.getInt("carrierHz")<20000||p.getInt("carrierHz")>60000
                ||p.getInt("definitionFieldCount")<1||p.getInt("definitionFieldCount")>10000
                ||(!raw&&!kind.equals("stateful"))||!(raw?control.equals("recorded"):(pana?control.equals("panasonic216"):control.equals("unavailable"))))
                throw new IllegalArgumentException("Invalid AC source profile identity or support status.");
            if(raw) {
                if(!RAW_ID.matcher(p.getJSONObject("control").getString("profile")).matches())throw new IllegalArgumentException("Invalid recorded AC link.");
                recorded++;
            }else {
                stateful++;
                if(pana&&!p.getJSONObject("control").getString("profile").equals("pana_"+model))throw new IllegalArgumentException("Invalid Panasonic AC link.");
                if(!pana&&p.getJSONObject("control").has("profile"))throw new IllegalArgumentException("Unavailable AC data has a control link.");
            }
            if(!control.equals("unavailable"))implemented++;
            if(records.put(id,p)!=null)throw new IllegalArgumentException("Duplicate AC source identity.");
        }
        if(records.size()!=734||recorded!=33||stateful!=701||implemented!=36||brands().size()!=45
            ||coverage.getInt("profiles")!=734||coverage.getInt("recorded")!=33||coverage.getInt("stateful")!=701
            ||coverage.getInt("controlsImplemented")!=36||coverage.getInt("controlsUnavailable")!=698
            ||coverage.getInt("brands")!=45||coverage.getInt("sourceRecordsOmitted")!=0)
            throw new IllegalArgumentException("Incomplete Mi APK AC source coverage.");
    }
    private static boolean isPanasonic(String model){for(String m:PANASONIC)if(m.equals(model))return true;return false;}
    int size(){return records.size();}
    Set<String> brands(){Set<String> result=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);for(JSONObject r:records.values())result.add(r.optString("brand"));return result;}
    List<JSONObject> profiles(String brand){List<JSONObject> result=new ArrayList<>();for(JSONObject r:records.values())if(brand==null||r.optString("brand").equalsIgnoreCase(brand))result.add(r);return result;}
    JSONObject readRecord(String id) throws Exception {
        JSONObject metadata=records.get(id);if(metadata==null)throw new IllegalArgumentException("Unknown AC source profile.");
        String raw;try(InputStream in=reader.open(metadata.getString("asset"))){raw=IrCatalog.readBounded(in,512*1024);}
        if(!IrCatalog.sha256(raw).equals(metadata.getString("assetSha256")))throw new IllegalArgumentException("AC source asset hash differs.");
        JSONObject value=new JSONObject(raw),row=value.getJSONObject("sourceRow"),brand=value.getJSONObject("brandSourceRow");
        if(value.getDouble("schemaVersion")!=1||!value.getString("id").equals(id)
            ||!value.getString("model").equals(metadata.getString("model"))||!value.getString("brand").equals(metadata.getString("brand"))
            ||!value.getString("sourceTable").equals("kk_remote_ircode")||!Boolean.FALSE.equals(value.get("programsExecuted"))
            ||!row.getString("ID").equals(metadata.getString("model"))||row.getInt("DEVICE_ID")!=3
            ||!String.valueOf(row.get("BRAND_ID")).equals(metadata.getString("brandId"))||row.getInt("FREQUENCY")!=metadata.getInt("carrierHz")
            ||row.getInt("VERSION")!=metadata.getInt("sourceVersion")
            ||(row.getInt("EXT_FLAG")==0)!=metadata.getString("definitionKind").equals("recorded")
            ||!String.valueOf(brand.get("ID")).equals(metadata.getString("brandId"))
            ||!String.valueOf(value.getJSONObject("brandData").get("id")).equals(metadata.getString("brandId"))
            ||value.getJSONObject("definition").length()!=metadata.getInt("definitionFieldCount"))
            throw new IllegalArgumentException("AC source data identity differs.");
        if(row.getInt("INDEXED")==1&&!value.getJSONObject("referencedSourceRow").getString("ID").equals(row.getString("IR_ZIP_KEY")))
            throw new IllegalArgumentException("AC source reference differs.");
        return value;
    }
}
