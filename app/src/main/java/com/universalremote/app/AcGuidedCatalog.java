package com.universalremote.app;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Routes only existing sendable controls; source-only AC definitions are never candidates. */
final class AcGuidedCatalog {
    final IrCatalog recorded;
    final AcSourceDatabase source;
    private final List<JSONObject> settings = new ArrayList<>();
    AcGuidedCatalog(IrCatalog recorded, AcSourceDatabase source, PanasonicAcEncoder panasonic, FamilyAcEncoder family) {
        this.recorded=recorded;this.source=source;
        if(panasonic!=null)settings.addAll(panasonic.profiles());
        if(family!=null)settings.addAll(family.profiles());
    }
    Set<String> brands() {
        Set<String> result=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if(recorded!=null)result.addAll(recorded.brands("air_conditioner"));
        if(source!=null)result.addAll(source.brands());
        for(JSONObject p:settings)result.add(p.optString("brand"));
        return result;
    }
    List<JSONObject> recorded(String brand) {
        return recorded==null?new ArrayList<>():recorded.forBrand(brand,"air_conditioner");
    }
    List<JSONObject> settings(String brand) {
        List<JSONObject> result=new ArrayList<>();
        for(JSONObject p:settings)if(p.optString("brand").equalsIgnoreCase(brand))result.add(p);
        return result;
    }
    int sendableCount(String brand) { return recorded(brand).size()+settings(brand).size(); }
    int unavailableSourceCount(String brand) {
        int count=0;if(source==null)return count;
        for(JSONObject p:source.profiles(brand))
            if(p.optJSONObject("control").optString("kind").equals("unavailable")&&!FamilyAcEncoder.supports(p.optString("model")))count++;
        return count;
    }
}
