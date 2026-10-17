package com.universalremote.app;

import android.content.Context;
import org.json.JSONObject;

/** Exercises the actual Android asset loader, including category separation after an upgrade. */
public final class CatalogDeviceChecks {
    private CatalogDeviceChecks(){}
    public static int run(Context context) throws Exception {
        IrCatalog catalog=new IrCatalog(context);int checks=0;
        if(catalog.forBrand("PRISM+").isEmpty())throw new AssertionError("PRISM+ missing from TV picker");checks++;
        if(catalog.forBrand("Daewoo").size()<3)throw new AssertionError("Daewoo handset profiles missing");checks++;
        if(catalog.brands("air_conditioner").size()<96)throw new AssertionError("AC catalogue was not loaded");checks++;
        for(JSONObject profile:catalog.forBrand("Samsung"))
            if(!profile.optString("category","tv").equals("tv"))throw new AssertionError("AC leaked into TV picker");
        checks++;
        if(catalog.forBrand("Samsung","air_conditioner").isEmpty())throw new AssertionError("Samsung AC capture missing");checks++;
        if(catalog.find("samsung_bn59")==null||catalog.find("lg_akb75375604")==null)throw new AssertionError("Old profile IDs broke upgrades");checks++;
        if(!catalog.forBrand("Daewoo").get(0).optString("category").equals("tv"))throw new AssertionError("Daewoo category incorrect");checks++;
        if(catalog.forBrand("Astro").size()<2)throw new AssertionError("Bundled Astro profiles missing; phone import must not be required");checks++;
        for(String id:new String[]{"astro_ultrabox_msb","astro_ultrabox_lsb"}) {
            JSONObject profile=catalog.find(id);
            if(profile==null||profile.getJSONObject("commands").length()!=29||profile.optBoolean("hardwareTested")||profile.optString("notice").isEmpty())
                throw new AssertionError("Astro buttons or untested-profile guidance missing: "+id);
            java.util.Iterator<String> keys=profile.getJSONObject("commands").keys();
            while(keys.hasNext())IrCatalog.checkedPattern(profile.getJSONObject("commands").getJSONObject(keys.next()));
            checks++;
        }
        for(String button:new String[]{"power","guide","record","red","green","yellow","blue","digit_0","digit_9"})
            if(!catalog.supports("astro_ultrabox_msb",button))throw new AssertionError("Astro button unavailable: "+button);
        checks++;
        return checks;
    }
}
