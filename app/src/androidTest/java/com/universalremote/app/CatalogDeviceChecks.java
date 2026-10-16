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
        return checks;
    }
}
