package com.universalremote.app;

import android.content.Context;
import org.json.JSONObject;
import java.util.List;

/** Checks actual APK Mi data and Android hardware services without sending IR. */
public final class CatalogDeviceChecks {
    private CatalogDeviceChecks() {}
    public static int run(Context context) throws Exception {
        IrCatalog catalog=new IrCatalog(context);int checks=0;
        if(catalog.profileCount()!=3706)throw new AssertionError("Mi index missing profiles");checks++;
        if(catalog.coverage().getInt("commands")!=140949)throw new AssertionError("Mi command coverage differs");checks++;
        for(String category:new String[]{"tv","set_top_box","air_conditioner","fan"}) {
            if(catalog.brands(category).isEmpty())throw new AssertionError("Missing category "+category);checks++;
            String brand=catalog.brands(category).iterator().next();
            List<JSONObject> profiles=catalog.forBrand(brand,category);
            JSONObject metadata=profiles.get(0);String id=metadata.getString("id");
            if(catalog.isLoaded(id)||metadata.has("commands"))throw new AssertionError("Eager signal load "+id);checks++;
            JSONObject loaded=catalog.loadProfile(id);JSONObject commands=loaded.getJSONObject("commands");
            if(commands.length()!=metadata.getInt("commandCount")||!catalog.isLoaded(id))throw new AssertionError("Lazy load failed "+id);
            java.util.Iterator<String> keys=commands.keys();
            while(keys.hasNext()) {
                String key=keys.next();JSONObject command=commands.getJSONObject(key);
                if(!key.equals(command.getString("sourceKey")))throw new AssertionError("Source key association changed");
                IrCatalog.checkedPattern(command);
            }
            checks++;
        }
        if(catalog.find("astro_ultrabox_msb")!=null||catalog.find("samsung_bn59")!=null)
            throw new AssertionError("Legacy IR profile survived");checks++;
        if(!catalog.forBrand("Astro","set_top_box").isEmpty()||!catalog.forBrand("PRISM+","tv").isEmpty())
            throw new AssertionError("Absent Mi brand was fabricated");checks++;
        if(catalog.forBrand("Samsung","tv").isEmpty()||catalog.forBrand("LG","tv").isEmpty())
            throw new AssertionError("Mi TV profiles missing");checks++;
        IrHardware.Result hardware=IrHardware.inspect(context);
        if(hardware.state==IrHardware.State.CHECKING||hardware.title.isEmpty()||hardware.hasEmitter!=(hardware.state==IrHardware.State.READY))
            throw new AssertionError("Android IR detection did not finish");checks++;
        checks+=verifyCredentialMigration(context,catalog);
        return checks;
    }
    private static int verifyCredentialMigration(Context context,IrCatalog catalog) throws Exception {
        DeviceStore store=new DeviceStore(context);List<JSONObject> original=store.load();
        String suffix=java.util.UUID.randomUUID().toString(),lg="test_lg_"+suffix,cloud="test_cloud_"+suffix;
        android.content.SharedPreferences prefs=context.getSharedPreferences("devices",Context.MODE_PRIVATE);
        try {
            JSONObject lgDevice=new JSONObject().put("id",lg).put("mode","lg").put("host","192.0.2.1").put("name","Migration probe LG");
            JSONObject cloudDevice=new JSONObject().put("id",cloud).put("mode","smartthings").put("cloudId","test-device").put("name","Migration probe Samsung");
            store.putCredential(lg,"test-pairing-value");store.putCredential(cloud,"test-authorization-value");
            String lgCipher=prefs.getString("credential."+lg,""),cloudCipher=prefs.getString("credential."+cloud,"");
            java.util.ArrayList<JSONObject> seed=new java.util.ArrayList<>();seed.add(lgDevice);seed.add(cloudDevice);
            seed.add(new JSONObject().put("id","old_ir_"+suffix).put("mode","ir").put("profile","astro_ultrabox_msb"));
            seed.add(new JSONObject().put("id","old_ac_"+suffix).put("mode","ac").put("profile","old_native_ac"));
            store.save(seed);
            if(store.removeUnavailableIrDevices(catalog)!=2)throw new AssertionError("Old IR entries were not pruned");
            List<JSONObject> retained=store.load();
            if(retained.size()!=2||!retained.get(0).toString().equals(lgDevice.toString())||!retained.get(1).toString().equals(cloudDevice.toString()))
                throw new AssertionError("Wi-Fi device metadata changed during migration");
            if(!"test-pairing-value".equals(store.credential(lg))||!"test-authorization-value".equals(store.credential(cloud)))
                throw new AssertionError("Android Keystore credentials no longer decrypt");
            if(!lgCipher.equals(prefs.getString("credential."+lg,""))||!cloudCipher.equals(prefs.getString("credential."+cloud,"")))
                throw new AssertionError("Encrypted Wi-Fi credential bytes changed");
            if(store.removeUnavailableIrDevices(catalog)!=0)throw new AssertionError("Migration is not idempotent");
            return 5;
        } finally {
            store.save(original);store.delete(lg);store.delete(cloud);
        }
    }
}
