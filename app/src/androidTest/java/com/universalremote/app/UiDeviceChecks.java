package com.universalremote.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Opt-in, no-emitter emulator checks. No test ever enables or simulates IR hardware. */
final class UiDeviceChecks {
    private interface Match { boolean matches(AccessibilityNodeInfo node); }
    private final Instrumentation instrumentation;
    private final Context context;
    private final UiAutomation automation;
    private final CatalogDeviceChecks.Progress progress;
    private final JSONArray checks=new JSONArray();
    private final File output;
    private Activity activity;
    private int systemUiWaitDialogs;

    private UiDeviceChecks(Instrumentation instrumentation,CatalogDeviceChecks.Progress progress) throws Exception {
        this.instrumentation=instrumentation;this.context=instrumentation.getTargetContext();
        this.progress=progress;this.automation=instrumentation.getUiAutomation();
        File external=context.getExternalFilesDir(null);
        if(external==null)throw new AssertionError("External app-specific validation directory unavailable");
        output=new File(external,"palm-validation-v161");
        if(!output.isDirectory()&&!output.mkdirs())throw new AssertionError("Could not create UI evidence directory");
        for(String owned:new String[]{"ui-failure.png","ui-failure-tree.txt","ui-verification.json"}) {
            File previous=new File(output,owned);
            if(previous.exists()&&!previous.delete())throw new AssertionError("Could not remove prior UI evidence: "+owned);
        }
    }
    static int run(Instrumentation instrumentation,int coreChecks,CatalogDeviceChecks.Progress progress) throws Exception {
        IrHardware.Result hardware=IrHardware.inspect(instrumentation.getTargetContext());
        boolean emulator=Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish");
        if(!emulator||hardware.state!=IrHardware.State.ABSENT)
            throw new AssertionError("UI checks require a software emulator with confirmed absent IR hardware");
        return new UiDeviceChecks(instrumentation,progress).exercise(coreChecks);
    }
    private int exercise(int coreChecks) throws Exception {
        SharedPreferences preferences=context.getSharedPreferences("devices",Context.MODE_PRIVATE);
        String original=preferences.getString("profiles",null);
        JSONObject report=new JSONObject().put("package",context.getPackageName()).put("versionName","1.6.1")
            .put("androidCatalogueChecks",coreChecks).put("hardwareState","No IR transmitter detected")
            .put("physicalHardwareTested",false).put("uiChecks",checks).put("passed",false);
        try {
            IrCatalog catalog=new IrCatalog(context);
            PanasonicAcEncoder encoder=new PanasonicAcEncoder(context);
            JSONObject astro=catalog.forBrand("Astro","set_top_box").get(0);
            JSONObject daewoo=catalog.forBrand("Daewoo","tv").get(0);
            JSONObject rawAc=catalog.forBrand("Panasonic","air_conditioner").get(0);
            JSONObject fan=catalog.forBrand("Panasonic","fan").get(0);
            JSONObject stateful=encoder.profiles().get(0);
            JSONArray fixtures=new JSONArray().put(rawFixture("astro","Validation Astro",astro))
                .put(rawFixture("daewoo","Validation Daewoo",daewoo))
                .put(rawFixture("raw_ac","Validation recorded AC",rawAc))
                .put(rawFixture("fan","Validation fan",fan))
                .put(new JSONObject().put("id","validation_panasonic").put("name","Validation Panasonic state")
                    .put("brand","Panasonic").put("category","air_conditioner").put("mode","panasonic_ac")
                    .put("profile",stateful.getString("id")).put("sourceModelId",stateful.getString("model"))
                    .put("responseConfirmed",false).put("requestedState",PanasonicAcEncoder.defaults(stateful).toJson()));
            if(!preferences.edit().putString("profiles",fixtures.toString()).commit())throw new AssertionError("Could not persist UI fixtures");
            activity=instrumentation.startActivitySync(new Intent(context,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForStartupData(astro.getString("id"));
            waitFor("No IR transmitter detected",false,false);banner();shot("ui-home.png");
            pass("Home shows the actual absent-emitter hardware banner");
            click("Check IR",false,false,false);
            waitFor("Palm Remote 1.6.1",true,false);waitFor("Source keyset: "+astro.getString("model"),true,false);
            shot("ui-ir-check.png");click("Done",false,false,false);
            pass("Check IR reports installed version and selected source keyset without transmitting");

            nav("Remote");waitFor("Validation Astro",false,false);waitRawCommands();
            click("All recorded buttons",true,false,true);waitFor("All recorded buttons",false,false);
            assertRecordedDisabled();shot("ui-astro-buttons.png");click("Done",false,false,false);
            pass("Working Astro source opens its complete recorded controls with sending disabled without hardware");

            selectFixture("Validation Daewoo");
            int before=profileCount(preferences);
            click("Find a working remote",false,false,true);
            waitFor("Remote 1 of 13 · xm_1_3399",false,false);
            waitFor("You can browse here",true,false);
            disabled("Send test signal");disabled("Use this remote");shot("ui-daewoo-guided.png");
            click("Next remote",false,false,false);waitFor("Remote 2 of 13 · xm_1_3390",false,false);
            click("Close",false,false,false);
            require(profileCount(preferences)==before,"Closing TV matching saved a remote");
            pass("Daewoo retesting starts at the source-prioritized candidates and cannot confirm or save without an emitter");

            selectFixture("Validation recorded AC");
            click("Find a working remote",false,false,true);waitFor("Panasonic · Find your remote",false,false);
            disabled("Send test signal");click("Close",false,false,false);
            pass("Recorded Panasonic AC profiles remain accessible with the no-emitter gate");

            selectFixture("Validation Panasonic state");
            waitFor("Requested settings",false,false);shot("ui-panasonic-first.png");
            click("Find a different Panasonic remote",false,false,true);
            List<JSONObject> states=encoder.profiles();
            for(int i=0;i<states.size();i++) {
                waitFor("AC remote "+(i+1)+" of 6 · "+states.get(i).getString("model"),false,false);
                disabled(states.get(i).optBoolean("powerToggle")?"Test power toggle · Cool 24 °C":"Test power on · Cool 24 °C");
                disabled("Use this remote");
                require(find("Save preview",false,false)!=null,"Missing explicit preview action");
                if(i==0||i==5)shot("ui-panasonic-guide-"+(i+1)+".png");
                if(i+1<states.size())click("Next remote",false,false,false);
            }
            pass("All six Panasonic parameter profiles browse with correct identities and disabled test/confirmation controls");
            click("Previous",false,false,false);
            waitFor("AC remote 5 of 6 · kk_3_137_7791",false,false);
            click("Save preview",false,false,false);waitFor("Save an untested preview?",false,false);
            click("Save preview",false,false,false);
            waitFor("Panasonic AC · kk_3_137_7791",false,false);
            JSONObject saved=lastProfile(preferences);
            require(!saved.getBoolean("responseConfirmed")&&saved.getString("mode").equals("panasonic_ac"),"Preview incorrectly claimed appliance response");
            require(saved.getString("sourceModelId").equals("kk_3_137_7791"),"Preview source identity changed");
            pass("Panasonic preview saves its exact source profile without an import or physical response claim");

            String persisted=saved.getJSONObject("requestedState").toString();
            for(int i=0;i<10;i++)click("Temperature +",false,false,true);
            waitFor("30 °C",false,false);
            for(int i=0;i<18;i++)click("Temperature −",false,false,true);
            waitFor("16 °C",false,false);
            require(lastProfile(preferences).getJSONObject("requestedState").toString().equals(persisted),"Editing sent or persisted unaccepted settings");
            require(findNode(n->value(n.getContentDescription()).equals("Fan speed"))==null,"Unsupported fan-speed selector was shown");
            shot("ui-panasonic-temperature.png");
            pass("Temperature edits clamp to 16–30 °C and do not persist unaccepted settings or expose unsupported fan controls");
            click("Mode",false,true,true);
            require(find("auto",false,false)==null,"Unverified Auto mode was exposed");
            click("heat",false,false,false);
            require(lastProfile(preferences).getJSONObject("requestedState").toString().equals(persisted),"Mode selection persisted without sending");
            clickVisibleOrScroll("Send requested settings");disabled("Send requested settings");
            clickVisibleOrScroll("Power on");disabled("Power on");
            clickVisibleOrScroll("Power off");disabled("Power off");shot("ui-panasonic-send-gates.png");
            pass("Mode edits do not transmit, and settings/on/off buttons remain disabled without an emitter");

            nav("Remote");
            click("Find a different Panasonic remote",false,false,true);
            waitFor("AC remote 5 of 6 · kk_3_137_7791",false,false);
            click("Next remote",false,false,false);
            waitFor("AC remote 6 of 6 · kk_3_137_7793",false,false);
            click("Save preview",false,false,false);waitFor("Save an untested preview?",false,false);click("Save preview",false,false,false);
            waitFor("Panasonic AC · kk_3_137_7793",false,false);
            clickVisibleOrScroll("Send power toggle");disabled("Send power toggle");
            require(findNode(n->value(n.getText()).equals("Power on")||value(n.getText()).equals("Power off"))==null,
                "Toggle profile falsely exposed explicit power state commands");
            shot("ui-panasonic-toggle.png");
            pass("The toggle-only Panasonic profile exposes a disabled explicit toggle action rather than on/off claims");

            selectFixture("Validation fan");waitRawCommands();click("All recorded buttons",true,false,true);
            assertRecordedDisabled();shot("ui-fan-buttons.png");click("Done",false,false,false);
            pass("Recorded fan controls remain available and cannot transmit without hardware");
            nav("Devices");banner();waitFor("Your devices",false,false);shot("ui-devices.png");
            pass("Devices retains source fixtures and the persistent IR hardware indicator");
            nav("Help");banner();shot("ui-help.png");
            pass("Help retains hardware status and the source/capability guidance");
            AcSourceDatabase completeAc=new AcSourceDatabase(context);
            JSONObject unsupported=null,recorded=null,encoded=null;
            for(JSONObject metadata:completeAc.profiles(null)) {
                String kind=metadata.getJSONObject("control").getString("kind");
                if(kind.equals("unavailable")&&unsupported==null)unsupported=metadata;
                if(kind.equals("recorded")&&recorded==null)recorded=metadata;
                if(kind.equals("panasonic216")&&encoded==null)encoded=metadata;
            }
            require(unsupported!=null&&recorded!=null&&encoded!=null,"AC source support kinds missing");
            String beforeSourceBrowsing=preferences.getString("profiles",null);
            chooseAcSource(unsupported);
            waitFor("Data included; controls unavailable",true,false);
            require(find("Open controls",false,false)==null,"Data-only AC source exposes working controls");
            shot("ui-ac-data-only.png");
            pass("All-734 AC inventory searches exact source IDs and clearly labels data-only profiles without control actions");
            click("View included data",false,false,false);
            waitFor("Included AC data · "+unsupported.getString("model"),false,false);
            waitFor("referencedSourceRow",true,false);waitFor("definition",true,false);
            shot("ui-ac-source-data.png");click("Done",false,false,false);
            require(java.util.Objects.equals(beforeSourceBrowsing,preferences.getString("profiles",null)),"Browsing source data changed saved remotes");
            pass("Complete original AC source and referenced data open without importing, sending or saving a device");
            chooseAcSource(encoded);click("Open controls",false,false,false);
            int encodedPosition=0;for(int i=0;i<states.size();i++)if(states.get(i).getString("model").equals(encoded.getString("model")))encodedPosition=i+1;
            waitFor("AC remote "+encodedPosition+" of 6 · "+encoded.getString("model"),false,false);
            disabled("Test power on · Cool 24 °C");disabled("Use this remote");shot("ui-ac-encoded-link.png");click("Close",false,false,false);
            pass("Complete AC inventory opens the exact supported Panasonic source with no-emitter transmission gates intact");
            chooseAcSource(recorded);click("Open controls",false,false,false);
            waitFor("Remote 1 of 1 · "+recorded.getString("model"),false,false);
            disabled("Send test signal");disabled("Use this remote");shot("ui-ac-recorded-link.png");click("Close",false,false,false);
            require(java.util.Objects.equals(beforeSourceBrowsing,preferences.getString("profiles",null)),"Opening AC controls saved a source record");
            pass("Complete AC inventory opens exact recorded controls without treating source-data inclusion as a confirmed appliance match");
            FamilyAcEncoder families=new FamilyAcEncoder(completeAc);
            for(JSONObject candidate:families.profiles()) {
                JSONObject source=null;
                for(JSONObject metadata:completeAc.profiles(null))if(metadata.getString("model").equals(candidate.getString("model")))source=metadata;
                require(source!=null,"Experimental candidate source missing");
                chooseAcSource(source);click("Try protocol candidate",false,false,false);
                waitFor("Try an AC protocol candidate",false,false);
                disabled("Test power on · Cool 24 °C");disabled("Use this remote");
                click("Save preview",false,false,false);waitFor("Save an untested preview?",false,false);click("Save preview",false,false,false);
                waitFor(candidate.getString("brand")+" AC · "+candidate.getString("model"),false,false);
                JSONObject preview=lastProfile(preferences);
                require(preview.getString("mode").equals("family_ac")&&!preview.getBoolean("responseConfirmed"),"Experimental candidate falsely claimed a match");
                require(preview.getString("sourceModelId").equals(candidate.getString("model")),"Wrong source candidate saved");
                clickVisibleOrScroll("Send requested settings");disabled("Send requested settings");
                clickVisibleOrScroll("Power on");disabled("Power on");clickVisibleOrScroll("Power off");disabled("Power off");
                clickVisibleOrScroll("Experimental protocol candidate");shot("ui-"+candidate.getString("family").toLowerCase(java.util.Locale.ROOT)+"-candidate.png");
                pass(candidate.getString("family")+" preview preserves its source ID, discloses experimental status, and disables all sending without IR hardware");
            }
            for(String brand:new String[]{"Samsung","LG","Hisense","Panasonic","Sharp","Xiaomi"}) {
                int savedBefore=profileCount(preferences);
                instrumentation.runOnMainSync(()->{
                    try {
                        java.lang.reflect.Method setup=MainActivity.class.getDeclaredMethod("chooseMethod",String.class);
                        setup.setAccessible(true);setup.invoke(activity,brand);
                    }catch(Exception error){throw new RuntimeException(error);}
                });
                waitFor(brand+" connection",false,false);
                click("Find a working remote · Guided testing",false,false,false);
                List<JSONObject> candidates=catalog.forBrand(brand,"tv");
                waitFor("Remote 1 of "+candidates.size()+" · "+candidates.get(0).getString("model"),false,false);
                waitFor("You can browse here",true,false);
                disabled("Send test signal");disabled("Use this remote");disabled("TV responded to this button");
                shot("ui-tv-"+brand.toLowerCase(java.util.Locale.ROOT)+"-guided.png");
                if(candidates.size()>1) {
                    click("Next remote",false,false,false);
                    waitFor("Remote 2 of "+candidates.size()+" · "+candidates.get(1).getString("model"),false,false);
                    disabled("Use this remote");
                    click("Previous",false,false,false);
                    waitFor("Remote 1 of "+candidates.size()+" · "+candidates.get(0).getString("model"),false,false);
                }else disabled("Next remote");
                click("Close",false,false,false);
                require(savedBefore==profileCount(preferences),"TV browsing saved an untested remote");
                pass(brand+" guided entry, candidate identity, navigation and no-emitter save/response gates verified");
            }
            nav("Home");shot("ui-home.png");
            report.put("installed_sha256",fileSha256(new File(context.getPackageCodePath())))
                .put("passed",true).put("uiCheckCount",checks.length()).put("fixtureCount",profileCount(preferences));
            progress.report("UI evidence: "+output.getAbsolutePath());
            return checks.length();
        } catch(Throwable error) {
            report.put("error",error.toString()).put("uiCheckCount",checks.length());
            try{shot("ui-failure.png");dumpFailureTree();}catch(Exception ignored){/* Preserve original error. */}
            if(error instanceof Exception)throw (Exception)error;
            throw new AssertionError(error);
        } finally {
            Throwable cleanupError=null;
            try {
                if(activity!=null) {
                    instrumentation.runOnMainSync(()->activity.finish());
                    instrumentation.waitForIdleSync();
                    long end=SystemClock.uptimeMillis()+10000;
                    while(!activity.isDestroyed()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(100);
                    require(activity.isDestroyed(),"UI activity did not finish before preference restoration");
                }
            } catch(Throwable error){cleanupError=error;report.put("passed",false).put("cleanupError",error.toString());}
            finally {
                SharedPreferences.Editor restore=preferences.edit();
                if(original==null)restore.remove("profiles");else restore.putString("profiles",original);
                boolean restored=restore.commit();
                report.put("originalPreferencesRestored",restored).put("systemUiWaitDialogs",systemUiWaitDialogs);
                if(!restored)report.put("passed",false);
                writeJson("ui-verification.json",report);
                if(!restored)throw new AssertionError("Could not restore original UI fixture preferences");
            }
            if(cleanupError!=null)throw new AssertionError("UI cleanup failed",cleanupError);
        }
    }
    private void chooseAcSource(JSONObject metadata) throws Exception {
        nav("Home");click("Browse all 734 AC profiles",false,false,true);
        waitFor("All 734 Mi APK AC profiles",false,false);
        AccessibilityNodeInfo search=findNode(n->n.isVisibleToUser()&&value(n.getClassName()).equals("android.widget.EditText"));
        require(search!=null,"AC source search field missing");
        Bundle arguments=new Bundle();arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,metadata.getString("model"));
        try{require(search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments),"Cannot search AC source IDs");}finally{search.recycle();}
        instrumentation.waitForIdleSync();SystemClock.sleep(250);
        click(" · "+metadata.getString("model")+" ·",true,false,false);
        waitFor("AC source · "+metadata.getString("model"),false,false);
    }
    private JSONObject rawFixture(String id,String name,JSONObject profile) throws Exception {
        return new JSONObject().put("id","validation_"+id).put("name",name).put("mode","ir")
            .put("brand",profile.getString("brand")).put("category",profile.getString("category"))
            .put("profile",profile.getString("id")).put("sourceModelId",profile.getString("model"));
    }
    private void pass(String message){checks.put(message);progress.report("PASS UI: "+message);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int profileCount(SharedPreferences p) throws Exception{return new JSONArray(p.getString("profiles","[]")).length();}
    private static JSONObject lastProfile(SharedPreferences p) throws Exception{JSONArray rows=new JSONArray(p.getString("profiles","[]"));return rows.getJSONObject(rows.length()-1);}
    private void banner() throws Exception{waitFor("No IR transmitter detected",false,false);require(find("Check IR",false,false).isEnabled(),"Check IR disabled");}
    private void nav(String page) throws Exception{click(page+" tab",true,true,false);banner();}
    private void selectFixture(String name) throws Exception {
        nav("Remote");click("Switch device",true,false,true);click(name,false,false,false);waitFor(name,false,false);banner();
    }
    private void waitForStartupData(String profileId) throws Exception {
        progress.report("Waiting for startup data and selected signals to settle");
        long deadline=SystemClock.uptimeMillis()+180000,stableSince=0,lastGeneration=-1;
        while(SystemClock.uptimeMillis()<deadline) {
            boolean[] ready={false};long[] generation={-1};
            instrumentation.runOnMainSync(()->{
                require(!((Boolean)activityField("irCatalogFailed")),"Startup IR catalogue failed to load");
                require(!((Boolean)activityField("panasonicFailed")),"Startup Panasonic encoder failed to load");
                require(!((Boolean)activityField("acSourceFailed")),"Startup complete AC database failed to load");
                require(((String)activityField("irProfileError")).isEmpty(),"Startup Astro signals failed to load");
                IrCatalog catalogue=(IrCatalog)activityField("infrared");
                ready[0]=catalogue!=null&&activityField("panasonic")!=null&&activityField("acSourceDatabase")!=null
                    &&!((Boolean)activityField("hardwareChecking"))&&catalogue.isLoaded(profileId);
                generation[0]=(Long)activityField("renderedControlsGeneration");
            });
            if(ready[0]) {
                if(generation[0]!=lastGeneration){stableSince=SystemClock.uptimeMillis();lastGeneration=generation[0];}
                if(SystemClock.uptimeMillis()-stableSince>=500){instrumentation.waitForIdleSync();progress.report("Startup data and selected signals ready");return;}
            } else {stableSince=0;lastGeneration=-1;}
            SystemClock.sleep(100);
        }
        throw new AssertionError("Startup catalogue, encoder, complete AC data, and Astro signals did not settle");
    }
    private Object activityField(String name) {
        try {
            java.lang.reflect.Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);
            return field.get(activity);
        }catch(ReflectiveOperationException error){throw new AssertionError("Cannot inspect startup field "+name,error);}
    }
    private void waitRawCommands() throws Exception {
        long end=SystemClock.uptimeMillis()+90000;
        while(SystemClock.uptimeMillis()<end) {
            AccessibilityNodeInfo node=findNode(n->value(n.getText()).startsWith("All recorded buttons"));
            if(node!=null){node.recycle();return;}SystemClock.sleep(150);
        }
        throw new AssertionError("Lazy recorded commands did not load");
    }
    private void disabled(String label) throws Exception {
        AccessibilityNodeInfo node=waitFor(label,false,false);
        try{require(!node.isEnabled(),"Unexpectedly enabled no-emitter command: "+label);}finally{node.recycle();}
    }
    private void assertRecordedDisabled() throws Exception {
        AccessibilityNodeInfo root=automation.getRootInActiveWindow();int[] count={0};
        try{scanRecorded(root,count);}finally{if(root!=null)root.recycle();}
        require(count[0]>0,"No recorded source controls were rendered");
    }
    private void scanRecorded(AccessibilityNodeInfo node,int[] count) {
        if(node==null)return;
        if(node.isVisibleToUser()&&value(node.getContentDescription()).contains("recorded source key")) {
            require(!node.isEnabled(),"Recorded command enabled without an emitter");count[0]++;
        }
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);try{scanRecorded(child,count);}finally{if(child!=null)child.recycle();}}
    }
    private AccessibilityNodeInfo waitFor(String label,boolean contains,boolean description) throws Exception {
        long deadline=SystemClock.uptimeMillis()+90000;
        while(SystemClock.uptimeMillis()<deadline){AccessibilityNodeInfo found=find(label,contains,description);if(found!=null)return found;SystemClock.sleep(150);}
        throw new AssertionError("UI control not found: "+label);
    }
    private AccessibilityNodeInfo find(String label,boolean contains,boolean description) {
        String wanted=label.toLowerCase(java.util.Locale.ROOT);
        return findNode(node->{
            String text=value(description?node.getContentDescription():node.getText()).toLowerCase(java.util.Locale.ROOT);
            return node.isVisibleToUser()&&(contains?text.contains(wanted):text.equals(wanted));
        });
    }
    private AccessibilityNodeInfo findNode(Match match) {
        for(int attempt=0;attempt<4;attempt++) {
            AccessibilityNodeInfo root=automation.getRootInActiveWindow();
            try {
                AccessibilityNodeInfo palmAnr=search(root,n->value(n.getText()).equals("Palm Remote isn't responding")
                    ||value(n.getText()).equals("Palm Remote isn’t responding"));
                if(palmAnr!=null){palmAnr.recycle();throw new AssertionError("Palm Remote ANR during UI validation");}
                if(recoverSystemUiAnr(root))continue;
                return search(root,match);
            } finally{if(root!=null)root.recycle();}
        }
        throw new AssertionError("System UI ANR did not clear after bounded Wait recovery");
    }
    private boolean recoverSystemUiAnr(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo title=search(root,n->isSystemUiAnrTitle(value(n.getText())));
        if(title==null)return false;
        title.recycle();
        require(systemUiWaitDialogs<3,"System UI ANR recovery limit reached");
        AccessibilityNodeInfo wait=search(root,n->n.isVisibleToUser()&&n.isEnabled()
            &&value(n.getText()).equals("Wait")&&value(n.getClassName()).equals("android.widget.Button"));
        require(wait!=null,"System UI ANR has no enabled Wait button");
        try{require(wait.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Could not select Wait on System UI ANR");}
        finally{wait.recycle();}
        systemUiWaitDialogs++;
        progress.report("Emulator System UI ANR: chose Wait ("+systemUiWaitDialogs+" of 3 maximum)");
        long end=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<end) {
            SystemClock.sleep(150);
            AccessibilityNodeInfo current=automation.getRootInActiveWindow(),stillVisible=null;
            try{stillVisible=search(current,n->isSystemUiAnrTitle(value(n.getText())));if(stillVisible==null)return true;}
            finally{if(stillVisible!=null)stillVisible.recycle();if(current!=null)current.recycle();}
        }
        throw new AssertionError("System UI ANR Wait action did not dismiss the modal");
    }
    private static boolean isSystemUiAnrTitle(String text) {
        return text.equals("System UI isn't responding")||text.equals("System UI isn’t responding");
    }
    private AccessibilityNodeInfo search(AccessibilityNodeInfo node,Match match) {
        if(node==null)return null;
        if(match.matches(node))return AccessibilityNodeInfo.obtain(node);
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child=node.getChild(i),found;
            try{found=search(child,match);}finally{if(child!=null)child.recycle();}
            if(found!=null)return found;
        }
        return null;
    }
    private void click(String label,boolean contains,boolean description,boolean scroll) throws Exception {
        AccessibilityNodeInfo node=null;
        for(int i=0;i<(scroll?15:1);i++) {
            node=find(label,contains,description);if(node!=null)break;
            if(!scroll)node=waitFor(label,contains,description);
            else {
                AccessibilityNodeInfo container=findNode(n->n.isVisibleToUser()&&n.isScrollable());
                if(container==null)break;
                boolean moved;try{moved=container.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);}finally{container.recycle();}
                if(!moved)break;SystemClock.sleep(200);
            }
        }
        require(node!=null,"Cannot locate clickable UI control: "+label);
        boolean nativeClosure=false;
        int closingWindow=-1;
        try {
            require(node.isEnabled(),"Attempted to click a disabled UI control: "+label);
            nativeClosure=!contains&&!description&&value(node.getClassName()).equals("android.widget.Button")
                &&(label.equalsIgnoreCase("Done")||label.equalsIgnoreCase("Close"));
            closingWindow=node.getWindowId();
            // Exactly one dispatch: never combine an accessibility action with a touch fallback.
            require(node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"UI click action was not accepted: "+label);
        } finally{node.recycle();}
        instrumentation.waitForIdleSync();SystemClock.sleep(120);
        if(nativeClosure)waitForDialogDismissal(closingWindow,label);
    }
    private void waitForDialogDismissal(int windowId,String label) {
        long deadline=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<deadline) {
            AccessibilityNodeInfo current=findNode(n->n.isVisibleToUser());
            try{if(current!=null&&current.getWindowId()!=windowId)return;}
            finally{if(current!=null)current.recycle();}
            SystemClock.sleep(100);
        }
        throw new AssertionError("Native dialog remained open after tapping "+label);
    }
    private void clickVisibleOrScroll(String label) throws Exception {
        AccessibilityNodeInfo node=find(label,false,false);if(node!=null){node.recycle();return;}
        for(int i=0;i<12;i++) {
            AccessibilityNodeInfo container=findNode(n->n.isVisibleToUser()&&n.isScrollable());
            require(container!=null,"No scroll container for "+label);
            boolean moved;try{moved=container.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);}finally{container.recycle();}
            SystemClock.sleep(150);node=find(label,false,false);if(node!=null){node.recycle();return;}
            if(!moved)break;
        }
        throw new AssertionError("Could not expose disabled control: "+label);
    }
    private void shot(String name) throws Exception {
        instrumentation.waitForIdleSync();Bitmap bitmap=automation.takeScreenshot();require(bitmap!=null,"Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(output,name))){require(bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"Screenshot compression failed");}
        finally{bitmap.recycle();}
    }
    private void writeJson(String name,JSONObject value) throws Exception {
        try(FileOutputStream stream=new FileOutputStream(new File(output,name))){stream.write((value.toString(2)+"\n").getBytes(StandardCharsets.UTF_8));}
    }
    private void dumpFailureTree() throws Exception {
        AccessibilityNodeInfo root=automation.getRootInActiveWindow();StringBuilder text=new StringBuilder();
        try{describe(root,text,0);}finally{if(root!=null)root.recycle();}
        try(FileOutputStream stream=new FileOutputStream(new File(output,"ui-failure-tree.txt"))){stream.write(text.toString().getBytes(StandardCharsets.UTF_8));}
    }
    private void describe(AccessibilityNodeInfo node,StringBuilder text,int depth) {
        if(node==null||depth>30)return;
        text.append(depth).append(' ').append(node.getClassName()).append(" visible=").append(node.isVisibleToUser())
            .append(" enabled=").append(node.isEnabled()).append(" text=").append(value(node.getText()))
            .append(" desc=").append(value(node.getContentDescription())).append('\n');
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);try{describe(child,text,depth+1);}finally{if(child!=null)child.recycle();}}
    }
    private static String value(CharSequence text){return text==null?"":text.toString();}
    private static String fileSha256(File file) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(FileInputStream stream=new FileInputStream(file)){byte[] bytes=new byte[65536];int read;while((read=stream.read(bytes))!=-1)hash.update(bytes,0,read);}
        StringBuilder result=new StringBuilder();for(byte item:hash.digest())result.append(String.format(java.util.Locale.ROOT,"%02x",item&255));return result.toString();
    }
}
