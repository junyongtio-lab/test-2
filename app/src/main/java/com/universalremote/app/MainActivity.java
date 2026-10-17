package com.universalremote.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import com.universalremote.app.protocols.*;
import com.universalremote.app.smartthings.SmartThingsClient;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/** Account-free, local-first controller. A command is successful only after the transport accepts it. */
public final class MainActivity extends Activity {
    private static final int BG=0xff0a111e, CARD=0xff141f30, EDGE=0xff263449,
        TEXT=0xfff2f5fa, MUTED=0xff9aabc0, MINT=0xff72e5c3;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService hardwareWorker = Executors.newSingleThreadExecutor();
    private IrHardware.Result irHardware = IrHardware.checking();
    private DeviceStore store;
    private IrCatalog infrared;
    private final List<JSONObject> devices = new ArrayList<>();
    private JSONObject selected;
    private NetworkRemote network;
    private SmartThingsClient cloud;
    private SmartThingsClient.Device cloudDevice;
    private LinearLayout root, content, nav;
    private TextView statusText;
    private TextView irHardwareTitle, irHardwareDetail;
    private Button irHardwareButton;
    private String currentPage="Remote", status="Choose a device to get started", notice="", irProfileError="";
    private boolean connected, busy, destroyed, hardwareChecking, irCatalogFailed;
    private long connectionGeneration;
    private final Map<View,String> commandButtons = new HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new DeviceStore(this);
        devices.addAll(store.load());
        if (!devices.isEmpty()) selected=devices.get(0);
        render();
        // Keep asset parsing off the first-frame path, including on slower Android phones.
        worker.execute(()->{
            try {
                IrCatalog loaded=new IrCatalog(this);
                ui(()->{
                    infrared=loaded;
                    try {
                        String selectedId=selected==null?"":selected.optString("id");
                        int removed=store.removeUnavailableIrDevices(loaded);
                        if(removed>0) {
                            devices.clear();devices.addAll(store.load());selected=null;
                            for(JSONObject device:devices)if(device.optString("id").equals(selectedId))selected=device;
                            if(selected==null&&!devices.isEmpty())selected=devices.get(0);
                            notice=removed+" previous IR remote(s) removed. Add a matching remote from the Mi Remote catalogue. Wi-Fi devices and credentials were retained.";
                        }
                    }catch(Exception error){notice="Could not update saved IR remotes: "+error.getMessage();}
                    render();if(selected!=null)connect();
                });
            } catch(Exception error) {
                ui(()->{irCatalogFailed=true;notice="IR profiles could not load: "+error.getMessage();render();if(selected!=null)connect();});
            }
        });
    }
    @Override protected void onResume() {
        super.onResume();
        checkIrHardware(false);
    }
    private void checkIrHardware(boolean showDetails) {
        if(hardwareChecking||destroyed)return;
        hardwareChecking=true;irHardware=IrHardware.checking();updateIrHardwareViews();updateIrRemoteAvailability();
        // A separate worker keeps phone detection independent of catalogue loading and TV pairing.
        hardwareWorker.execute(()->{
            IrHardware.Result result=IrHardware.inspect(getApplicationContext());
            ui(()->{
                hardwareChecking=false;irHardware=result;updateIrHardwareViews();updateIrRemoteAvailability();
                if(showDetails)showIrHardwareDetails();
            });
        });
    }
    private void updateIrHardwareViews() {
        if(irHardwareTitle==null)return;
        irHardwareTitle.setText(irHardware.title);
        irHardwareTitle.setTextColor(irHardware.hasEmitter?MINT:TEXT);
        irHardwareDetail.setText(irHardware.detail);
        irHardwareButton.setEnabled(!hardwareChecking);
        irHardwareButton.setText(hardwareChecking?"Checking…":"Check IR");
    }
    private void showIrHardwareDetails() {
        String version="";
        try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){/* Optional display metadata. */}
        String details="Palm Remote "+version+"\nPhone: "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL
            +"\nAndroid: "+android.os.Build.VERSION.RELEASE+"\n\n"+irHardware.diagnostics
            +"\n\nThis checks the phone's built-in IR hardware. It does not test whether your TV, decoder or air conditioner responds. USB accessories and external IR hubs are not supported by this APK.";
        new AlertDialog.Builder(this).setTitle(irHardware.title).setMessage(details)
            .setPositiveButton("Done",null).setNeutralButton("Check again",(dialog,which)->checkIrHardware(true)).show();
    }
    private boolean selectedUsesIr() {
        return selected!=null&&selected.optString("mode").equals("ir");
    }
    private void updateIrRemoteAvailability() {
        if(!selectedUsesIr())return;
        if(infrared==null) {
            updateStatus(irCatalogFailed?"IR catalogue could not load. Restart the app to retry.":"IR catalogue is loading…",false);
        } else if(infrared.find(selected.optString("profile"))==null) {
            updateStatus("This remote is no longer in the Mi Remote catalogue. Add a matching remote again.",false);
        } else if(!irProfileError.isEmpty()) {
            updateStatus(irProfileError,false);
        } else if(!infrared.isLoaded(selected.optString("profile"))) {
            updateStatus("Loading Mi Remote signals…",false);
        } else updateStatus(irHardware.title+(irHardware.hasEmitter?" · device response unverified":""),irHardware.hasEmitter);
    }
    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), EDGE); return d;
    }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v=new TextView(this); v.setText(value); v.setTextColor(color); v.setTextSize(size);
        v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        v.setLineSpacing(dp(3),1); return v;
    }
    private void gap(LinearLayout into,int height) { View v=new View(this); into.addView(v,new LinearLayout.LayoutParams(1,dp(height))); }
    private Button action(String title, Runnable onClick, boolean primary) {
        Button b=new Button(this); b.setText(title); b.setTextSize(14); b.setAllCaps(false);
        b.setTextColor(primary?BG:TEXT); b.setBackground(shape(primary?MINT:CARD,16));
        b.setMinHeight(dp(52)); b.setMinimumHeight(dp(52)); b.setPadding(dp(12),0,dp(12),0);
        b.setOnClickListener(v->{v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); onClick.run();});
        return b;
    }
    private void weighted(LinearLayout into,View view,int height) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),1); p.setMargins(dp(4),dp(4),dp(4),dp(4)); into.addView(view,p);
    }
    private void full(LinearLayout into,View view,int height) { into.addView(view,new LinearLayout.LayoutParams(-1,dp(height))); }
    private void render() {
        commandButtons.clear();statusText=null;
        root=column(); root.setBackgroundColor(BG); root.setPadding(dp(20),dp(8),dp(20),0);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if (android.os.Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(dp(20),bars.top+dp(8),dp(20),bars.bottom);
            } else v.setPadding(dp(20),insets.getSystemWindowInsetTop()+dp(8),dp(20),insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        LinearLayout header=row();
        LinearLayout brand=column(); brand.addView(text("PALM / REMOTE",12,MINT,true)); gap(brand,4);
        brand.addView(text(currentPage.equals("Remote")?"Your home, in hand.":currentPage,25,TEXT,true));
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button plus=action("+",this::addBrand,false); plus.setContentDescription("Add a remote control");
        header.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(48))); root.addView(header); gap(root,16);
        LinearLayout hardware=row();hardware.setPadding(dp(12),dp(10),dp(12),dp(10));hardware.setBackground(shape(CARD,16));
        LinearLayout hardwareLabels=column();
        irHardwareTitle=text(irHardware.title,16,TEXT,true);irHardwareTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        hardwareLabels.addView(irHardwareTitle);gap(hardwareLabels,3);
        irHardwareDetail=text(irHardware.detail,12,MUTED,false);hardwareLabels.addView(irHardwareDetail);
        hardware.addView(hardwareLabels,new LinearLayout.LayoutParams(0,-2,1));
        irHardwareButton=action("Check IR",()->checkIrHardware(true),false);
        irHardwareButton.setContentDescription("Check infrared transmitter");
        LinearLayout.LayoutParams checkParams=new LinearLayout.LayoutParams(dp(105),dp(52));checkParams.setMargins(dp(8),0,0,0);
        hardware.addView(irHardwareButton,checkParams);root.addView(hardware,new LinearLayout.LayoutParams(-1,-2));gap(root,12);
        updateIrHardwareViews();
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false);
        content=column(); content.setPadding(0,0,0,dp(20)); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if (currentPage.equals("Remote")) remotePage();
        else if (currentPage.equals("Devices")) devicesPage(); else helpPage();
        nav=row(); nav.setPadding(0,dp(8),0,dp(8));
        for (String page:new String[]{"Remote","Devices","Help"}) {
            Button b=action(page,()->{currentPage=page;render();},currentPage.equals(page)); weighted(nav,b,48);
        }
        root.addView(nav);
        root.requestApplyInsets();
    }
    private LinearLayout card(String heading,String body) {
        LinearLayout card=column(); card.setPadding(dp(18),dp(16),dp(18),dp(16)); card.setBackground(shape(CARD,20));
        if (!heading.isEmpty()) card.addView(text(heading,17,TEXT,true));
        if (!body.isEmpty()) { gap(card,8); card.addView(text(body,14,MUTED,false)); }
        content.addView(card,new LinearLayout.LayoutParams(-1,-2));gap(content,14);return card;
    }
    private void remotePage() {
        if (selected==null) {
            LinearLayout c=card("TVs, decoders, ACs & fans.","Choose a matching remote from the Mi Remote IR catalogue. No Palm account, subscriptions or ads.");
            gap(c,16); full(c,action("Add your first remote",this::addBrand,true),54);
            card("The right connection", "Infrared requires a compatible Android phone and a matching Mi Remote keyset. Samsung SmartThings and supported LG webOS Wi-Fi pairing are also available. Astro, NJOI and PRISM+ were not found in this database snapshot.");
            card("Built for your Android phone", "An APK cannot run on iPhone. Most Android phones also have no IR transmitter; check your phone before choosing infrared.");
            if (!notice.isEmpty()) card("Notice",notice);
            return;
        }
        if(selected.optString("mode").equals("ir")){recordedIrPage();return;}
        if(!usesNetwork(selected)){legacyUnavailablePage();return;}
        LinearLayout device=card(selected.optString("name"), selected.optString("brand")+"  ·  "+connectionLabel(selected));
        statusText=text(status,13,connected?MINT:MUTED,false);gap(device,8);device.addView(statusText);
        statusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        gap(device,10);full(device,action("Switch device  ▾",this::pickDevice,false),44);
        LinearLayout top=row(); command(top,"⏻  Power","power",true);command(top,"Input","input",false); content.addView(top);
        gap(content,12);
        LinearLayout pad=column(); pad.setPadding(dp(8),dp(8),dp(8),dp(8));pad.setBackground(shape(CARD,28));
        LinearLayout p1=row(); spacer(p1);command(p1,"▲","up",false);spacer(p1);pad.addView(p1);
        LinearLayout p2=row();command(p2,"◀","left",false);command(p2,"OK","ok",true);command(p2,"▶","right",false);pad.addView(p2);
        LinearLayout p3=row();spacer(p3);command(p3,"▼","down",false);spacer(p3);pad.addView(p3);content.addView(pad);
        LinearLayout shortcuts=row();command(shortcuts,"Back","back",false);command(shortcuts,"Home","home",false);command(shortcuts,"Menu","menu",false);content.addView(shortcuts);
        gap(content,12);
        LinearLayout sound=row();
        LinearLayout vol=column(),ch=column(),center=column();
        vol.addView(text("VOLUME",11,MUTED,true));full(vol,key("+","volume_up",false),54);gap(vol,5);full(vol,key("−","volume_down",false),54);
        center.addView(text("SOUND",11,MUTED,true));full(center,key("Mute","mute",false),54);gap(center,5);full(center,key("Info","info",false),54);
        ch.addView(text("CHANNEL",11,MUTED,true));full(ch,key("+","channel_up",false),54);gap(ch,5);full(ch,key("−","channel_down",false),54);
        weighted(sound,vol,144);weighted(sound,center,144);weighted(sound,ch,144);content.addView(sound);
        LinearLayout playback=row();command(playback,"Rewind","rewind",false);
        if(selected.optString("mode").equals("lg")) {
            command(playback,"Play","play",false);command(playback,"Pause","pause",false);
        } else command(playback,"Play / pause","play_pause",false);
        command(playback,"Forward","fast_forward",false);content.addView(playback);
        LinearLayout guide=row();command(guide,"Guide","guide",false);guide.addView(action("123  Keypad",this::keypad,false),new LinearLayout.LayoutParams(0,dp(56),1));content.addView(guide);
        gap(content,14);
        full(content,action("Reconnect",this::connect,false),48);
        gap(content,14);
        content.addView(text("Only buttons supported by this connection are enabled. Pairing and model support depend on your TV.",12,MUTED,false));
        if (!notice.isEmpty()) {gap(content,10);content.addView(text(notice,13,MUTED,false));}
        refreshButtons();
    }
    private boolean usesNetwork(JSONObject device) {
        String mode=device.optString("mode");return mode.equals("lg")||mode.equals("smartthings");
    }
    private void legacyUnavailablePage() {
        updateStatus("This saved remote is unavailable in the Mi Remote catalogue.",false);
        deviceHeading();
        card("Choose a replacement remote", "This older remote cannot send commands in this version. Add a recorded remote from the Mi Remote catalogue for your appliance. You can remove this old entry from Devices.");
        full(content,action("Add a recorded remote",this::addBrand,true),54);gap(content,12);
        full(content,action("Open saved devices",()->{currentPage="Devices";render();},false),48);
    }
    private String connectionLabel(JSONObject device) {
        switch(device.optString("mode")) { case "ir":return "Mi Remote infrared";case "lg":return "Local Wi-Fi";case "smartthings":return "SmartThings cloud";default:return "Unavailable legacy remote"; }
    }
    private void spacer(LinearLayout row) { row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(58),1)); }
    private void command(LinearLayout row,String label,String code,boolean primary) { weighted(row,key(label,code,primary),58); }
    private Button key(String label,String code,boolean primary) {
        Button b=action(label,()->send(code),primary); b.setContentDescription(label);commandButtons.put(b,code);return b;
    }
    private String labelFor(String code) { return code.replace('_',' '); }
    private RemoteCommand networkCommand(String code) {
        String name=code.equals("ok")?"SELECT":code.equals("input")?"SOURCE":code.toUpperCase(Locale.ROOT);
        try{return RemoteCommand.valueOf(name);}catch(Exception e){return null;}
    }
    private boolean supports(String code) {
        if (selected==null || !connected || busy) return false;
        if (selected.optString("mode").equals("ir")) return infrared!=null && infrared.supports(selected.optString("profile"),code);
        if(!usesNetwork(selected))return false;
        RemoteCommand command=networkCommand(code);if(command==null)return false;
        if (cloud!=null && cloudDevice!=null) {
            if(code.equals("input")) return cloudDevice.getCapabilities().contains("mediaInputSource");
            return cloud.supports(command,cloudDevice);
        }
        return network!=null && network.supports(command);
    }
    private void refreshButtons() {
        for (Map.Entry<View,String> entry:commandButtons.entrySet()) {
            boolean enabled=supports(entry.getValue());entry.getKey().setEnabled(enabled);entry.getKey().setAlpha(enabled?1f:.3f);
        }
    }
    private void updateStatus(String value,boolean ready) {
        status=value;connected=ready;
        if(statusText!=null && currentPage.equals("Remote")) {statusText.setText(value);statusText.setTextColor(ready?MINT:MUTED);}
        refreshButtons();
    }
    private void send(String command) {
        if (!supports(command)) {message("This button is unavailable for this connection.");return;}
        if(command.equals("input") && cloud!=null){cloudInput();return;}
        final JSONObject device=selected; final long generation=connectionGeneration;
        final NetworkRemote targetNetwork=network; final SmartThingsClient targetCloud=cloud;
        final SmartThingsClient.Device targetCloudDevice=cloudDevice;
        busy=true;refreshButtons();
        worker.execute(()->{
            try {
                if (device.optString("mode").equals("ir")) infrared.transmit(device.optString("profile"),command);
                else if (targetCloud!=null) targetCloud.send(networkCommand(command),targetCloudDevice);
                else if(targetNetwork!=null) targetNetwork.send(networkCommand(command));
                else throw new IllegalStateException("Connect the TV first.");
                ui(()->{if(generation==connectionGeneration)updateStatus(device.optString("mode").equals("ir")?"IR sent · "+commandLabel(device,command):"Command sent · "+labelFor(command),true);});
            }catch(Exception error){ui(()->{if(generation==connectionGeneration) {updateStatus(error.getMessage(),connected);message(error.getMessage());}});}
            finally {ui(()->{if(generation==connectionGeneration){busy=false;refreshButtons();}});}
        });
    }
    private void connect() {
        connectionGeneration++; final long generation=connectionGeneration;
        if (network!=null) network.dispose(); network=null;cloud=null;cloudDevice=null;connected=false;busy=false;
        irProfileError="";
        if(selected==null)return;
        JSONObject device=selected;
        if(device.optString("mode").equals("ir")) {
            updateIrRemoteAvailability();
            if(infrared==null||infrared.isLoaded(device.optString("profile")))return;
            final IrCatalog catalogue=infrared;
            worker.execute(()->{
                try {
                    catalogue.loadProfile(device.optString("profile"));
                    ui(()->{if(generation==connectionGeneration){render();updateIrRemoteAvailability();}});
                }catch(Exception error){
                    ui(()->{if(generation==connectionGeneration){irProfileError="Signals could not load: "+error.getMessage();render();updateIrRemoteAvailability();}});
                }
            });
            return;
        }
        if(!usesNetwork(device)) {
            updateStatus("This saved remote is unavailable in the Mi Remote catalogue.",false);return;
        }
        updateStatus("Connecting…",false);
        worker.execute(()->{
            try {
                if(device.optString("mode").equals("lg")) {
                    NetworkRemote remote=new NetworkRemote(new DeviceEndpoint(TvPlatform.LG_WEBOS,device.getString("host"),store.credential(device.getString("id"))),
                        result->ui(()->{if(generation==connectionGeneration)updateStatus(result.getMessage(),result.getPhase()==RemoteStatus.Phase.READY);}),
                        credential->{try{store.putCredential(device.optString("id"),credential);}catch(Exception e){ui(()->message("Could not save the TV pairing. Pair again next time."));}});
                    ui(()->{if(generation==connectionGeneration)network=remote;else remote.dispose();});
                    remote.connect();
                }else {
                    SmartThingsClient client=new SmartThingsClient(store.credential(device.getString("id")));
                    SmartThingsClient.Device match=null;
                    for(SmartThingsClient.Device candidate:client.listDevices())if(candidate.getId().equals(device.getString("cloudId")))match=candidate;
                    if(match==null)throw new IllegalStateException("This device was not returned by SmartThings. Check token permissions or add it again.");
                    SmartThingsClient.Device found=match;
                    ui(()->{if(generation==connectionGeneration){cloud=client;cloudDevice=found;updateStatus("SmartThings connected · internet required",true);}});
                }
            }catch(Exception error){ui(()->{if(generation==connectionGeneration)updateStatus(error.getMessage(),false);});}
        });
    }
    private void devicesPage() {
        card("Your devices",devices.isEmpty()?"Add a TV, decoder, air conditioner or fan. Your device list stays on this phone.":"Saved on this phone. Tap a device to open its remote.");
        for(JSONObject device:devices) {
            LinearLayout c=card(device.optString("name"),device.optString("brand")+" · "+connectionLabel(device));
            LinearLayout actions=row();weighted(actions,action("Open",()->select(device),true),48);
            weighted(actions,action("Remove",()->new AlertDialog.Builder(this).setTitle("Remove remote?").setMessage(device.optString("name"))
                .setNegativeButton("Cancel",null).setPositiveButton("Remove",(dialog,which)->{
                    devices.remove(device);store.delete(device.optString("id"));store.save(devices);
                    if(selected==device){selected=devices.isEmpty()?null:devices.get(0);connect();}render();}).show(),false),48);c.addView(actions);
        }
        full(content,action("+ Add a remote",this::addBrand,true),54);gap(content,12);
    }
    private void helpPage() {
        card("Mi Remote infrared catalogue", "All bundled IR signals come from the JSON database published by github.com/ysard/mi_remote_database. Previous bundled IR datasets, user-imported profiles and generated AC protocols are removed. There is no profile-import step. No Palm account, subscriptions, advertisements or analytics.");
        if(infrared!=null)card("Available remote keysets",infrared.profileCount()+" keysets across televisions, set-top boxes, air conditioners and fans. Search by the database brand, then try a recorded remote on your appliance. Source keyset IDs are not retail model numbers; a brand match alone does not establish compatibility.");
        card("Astro, NJOI and PRISM+", "These names were not found in the bundled Mi Remote snapshot. The previous unverified Astro codes have been removed. This build does not provide an Astro Ultra Box / Ulti Box profile. A decoder profile from another brand must not be assumed compatible.");
        card("Recorded buttons", "Every remote shows the actual decoded command labels from its source JSON. Only those captured signals are available. The app does not invent missing buttons or generate arbitrary temperatures, modes or fan speeds. Some source files cannot be decoded and are excluded.");
        card("Air conditioners and fans", "A recorded AC command may change several settings at once. Labels describe the source capture, not readings from the appliance. Choose the matching keyset and test each command. The app cannot read or synchronize appliance state.");
        card("Samsung", "Use the published SmartThings API with an existing Samsung account and a token permitted to read devices and execute commands. Only capabilities exposed by your TV are enabled. Power-on depends on TV network settings.");
        card("LG webOS", "Connect the phone and TV to the same trusted Wi-Fi. Enter the TV’s local IP and approve the pairing prompt. This build uses LG’s published Connect SDK protocol on port 3000. Secure-only models are not supported by this transport.");
        card("Check this phone's infrared", "The hardware indicator above stays visible on every tab, even before adding a device. Tap Check IR to see this phone's model, app version and supported carrier frequencies. IR ready confirms Android detected a built-in emitter; it does not confirm the device responded. No IR transmitter detected means Android exposes no built-in emitter to this app. If a hardware query fails, IR check unavailable is shown instead.");
        card("Your data stays yours", "Device names stay in private app storage. Pairing keys and SmartThings tokens use Android Keystore encryption. Samsung commands go to api.smartthings.com; LG commands go directly to your TV. Android backup is disabled. Remove devices or clear app data to delete saved credentials.");
        card("Test status", "Automated build and protocol tests do not establish physical device compatibility. This APK needs an Android phone with a supported built-in IR transmitter for infrared. It cannot run on iPhone, and external IR accessories are not supported.");
    }
    private void addBrand() {
        String[] labels={"Television","Set-top box / decoder","Air conditioner","Fan"};
        String[] categories={"tv","set_top_box","air_conditioner","fan"};
        new AlertDialog.Builder(this).setTitle("Add a device").setItems(labels,(dialog,index)->{
            String category=categories[index];
            if(category.equals("set_top_box")) {
                new AlertDialog.Builder(this).setTitle("Mi Remote decoder catalogue")
                    .setMessage("Astro and NJOI were not found in this database snapshot. Browse the listed brands for a matching remote; no Astro profile is bundled.")
                    .setPositiveButton("Browse brands",(d,w)->categoryBrands(category)).setNegativeButton("Cancel",null).show();
            }else categoryBrands(category);
        }).setNegativeButton("Cancel",null).show();
    }
    private String categoryLabel(String category) {
        switch(category){case "set_top_box":return "Decoder";case "air_conditioner":return "Air-conditioner";case "fan":return "Fan";default:return "TV";}
    }
    private void categoryBrands(String category) {
        if(infrared==null){message(irCatalogFailed?"The IR catalogue could not load. Restart the app to retry.":"The IR catalogue is loading. Try again in a moment.");return;}
        Set<String> brands=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        brands.addAll(infrared.brands(category));
        // These two connection options remain available independently of the IR catalogue.
        if(category.equals("tv")){brands.add("Samsung");brands.add("LG");}
        List<Choice> options=new ArrayList<>();
        for(String brand:brands)options.add(new Choice(brand,()->{
            if(category.equals("tv")&&(brand.equalsIgnoreCase("Samsung")||brand.equalsIgnoreCase("LG")))chooseMethod(brand);
            else irSetup(brand,category);
        }));
        if(options.isEmpty()){message("No decoded "+categoryLabel(category).toLowerCase(Locale.ROOT)+" profiles are available in this snapshot.");return;}
        choices(categoryLabel(category)+" brand · Mi Remote",options);
    }
    private static final class Choice {
        final String label;final Runnable action;
        Choice(String label,Runnable action){this.label=label;this.action=action;}
        @Override public String toString(){return label;}
    }
    private void choices(String title,List<Choice> options) {
        LinearLayout body=column();body.setPadding(dp(18),0,dp(18),0);
        EditText search=input("Search this list",false);body.addView(search);
        ListView list=new ListView(this);body.addView(list,new LinearLayout.LayoutParams(-1,dp(330)));
        List<Choice> filtered=new ArrayList<>(options);
        ArrayAdapter<Choice> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,filtered);list.setAdapter(adapter);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(body).setNegativeButton("Cancel",null).create();
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                filtered.clear();String query=s.toString().toLowerCase(Locale.ROOT);
                for(Choice choice:options)if(choice.label.toLowerCase(Locale.ROOT).contains(query))filtered.add(choice);
                adapter.notifyDataSetChanged();
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        list.setOnItemClickListener((parent,view,index,id)->{Choice choice=filtered.get(index);dialog.dismiss();choice.action.run();});
        dialog.show();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    private void deviceHeading() {
        LinearLayout device=card(selected.optString("name"),selected.optString("brand")+" · "+connectionLabel(selected));
        statusText=text(status,13,connected?MINT:MUTED,false);statusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        gap(device,8);device.addView(statusText);gap(device,10);full(device,action("Switch device  ▾",this::pickDevice,false),44);
    }
    private void recordedIrPage() {
        deviceHeading();
        JSONObject profile=infrared==null?null:infrared.find(selected.optString("profile"));
        if(profile==null) {
            card("Remote unavailable",infrared==null?"The Mi Remote catalogue is loading…":"Add this remote again from the Mi Remote catalogue.");
        }else {
            String category=profile.optString("category","tv");
            String guidance="Signals and button labels come from the Mi Remote JSON keyset. Aim the phone's IR emitter at the appliance. Sending cannot confirm a response; exact model compatibility is untested.";
            if(category.equals("air_conditioner"))guidance+=" Each recorded AC command may change several settings at once. Arbitrary temperature or mode generation is unavailable.";
            card("Recorded "+categoryLabel(category).toLowerCase(Locale.ROOT)+" commands",guidance);
            content.addView(text("Source keyset: "+profile.optString("model",profile.optString("id")),12,MUTED,false));gap(content,10);
            JSONObject commands=profile.optJSONObject("commands");
            if(commands==null)card("Signals",irProfileError.isEmpty()?"Loading this keyset’s recorded signals…":irProfileError);
            else {
                List<String> keys=new ArrayList<>();Iterator<String> names=commands.keys();while(names.hasNext())keys.add(names.next());
                // Stable source-key ordering retains every actual command, including uncommon controls.
                Collections.sort(keys);
                LinearLayout line=null;
                for(int index=0;index<keys.size();index++) {
                    if(index%2==0){line=row();content.addView(line,new LinearLayout.LayoutParams(-1,-2));}
                    String code=keys.get(index);JSONObject signal=commands.optJSONObject(code);
                    String label=signal==null?code:signal.optString("label",signal.optString("sourceKey",code));
                    Button button=key(label,code,false);button.setMinHeight(dp(64));button.setMinimumHeight(dp(64));
                    button.setPadding(dp(8),dp(10),dp(8),dp(10));
                    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(4),dp(4),dp(4),dp(4));line.addView(button,params);
                }
                if(keys.size()%2==1)line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
                if(keys.isEmpty())card("No decoded buttons","This keyset has no available commands. Choose another matching keyset.");
            }
        }
        gap(content,14);full(content,action("Reload remote",this::connect,false),48);
        if(!notice.isEmpty()){gap(content,10);content.addView(text(notice,13,MUTED,false));}
        refreshButtons();
    }
    private String commandLabel(JSONObject device,String command) {
        JSONObject profile=infrared==null?null:infrared.find(device.optString("profile"));
        JSONObject commands=profile==null?null:profile.optJSONObject("commands");
        JSONObject signal=commands==null?null:commands.optJSONObject(command);
        return signal==null?labelFor(command):signal.optString("label",command);
    }
    private void chooseMethod(String brand) {
        List<String> options=new ArrayList<>();
        if(brand.equalsIgnoreCase("Samsung"))options.add("SmartThings · published Samsung API");
        if(brand.equalsIgnoreCase("LG"))options.add("webOS · local Wi-Fi pairing");
        if(infrared!=null&&!infrared.forBrand(brand,"tv").isEmpty())options.add("Mi Remote infrared · requires phone IR transmitter");
        new AlertDialog.Builder(this).setTitle(brand+" connection").setItems(options.toArray(new String[0]),(dialog,index)->{
            if(options.get(index).startsWith("SmartThings"))smartThingsSetup();
            else if(options.get(index).startsWith("webOS"))lgSetup();else irSetup(brand,"tv");
        }).setNegativeButton("Cancel",null).show();
    }
    private void irSetup(String brand,String category) {
        if(infrared==null){message("IR catalogue is unavailable.");return;}
        List<Choice> options=new ArrayList<>();
        List<JSONObject> profiles=infrared.forBrand(brand,category);
        for(int index=0;index<profiles.size();index++) {
            JSONObject profile=profiles.get(index);
            final String remoteName="Remote "+(index+1);
            String label=remoteName;
            if(profile.has("commandCount"))label+=" · "+profile.optInt("commandCount")+" recorded buttons";
            options.add(new Choice(label,()->{
                if(!irHardware.hasEmitter) {
                    new AlertDialog.Builder(this).setTitle(irHardware.title)
                        .setMessage("You can save this Mi Remote keyset. IR sending stays disabled until a built-in emitter is detected. Tap Check IR above the page for phone hardware details. A matching appliance model is required.")
                        .setPositiveButton("Save anyway",(d,w)->saveIr(brand,profile,remoteName)).setNegativeButton("Cancel",null).show();
                }else saveIr(brand,profile,remoteName);
            }));
        }
        if(options.isEmpty()){message("No decoded Mi Remote keysets are available for this brand and category.");return;}
        choices(brand+" · Mi Remote keyset",options);
    }
    private void saveIr(String brand,JSONObject profile,String remoteName) {
        try {
            JSONObject device=baseDevice(brand,"ir",brand+" · "+remoteName);
            device.put("profile",profile.getString("id"));device.put("category",profile.optString("category","tv"));
            JSONObject source=profile.optJSONObject("source");if(source!=null)device.put("sourceModelId",source.optString("modelId"));
            saveDevice(device);
        }catch(Exception e){message(e.getMessage());}
    }
    private JSONObject baseDevice(String brand,String mode,String name)throws Exception {
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("brand",brand).put("mode",mode).put("name",name).put("category","tv");
    }
    private void saveDevice(JSONObject device) {devices.add(device);store.save(devices);select(device);}
    private void select(JSONObject device) {selected=device;currentPage="Remote";status="Connecting…";notice="";render();connect();}
    private void pickDevice() {
        if(devices.isEmpty()){addBrand();return;}
        String[] names=new String[devices.size()+1];for(int i=0;i<devices.size();i++)names[i]=devices.get(i).optString("name");names[devices.size()]="+ Add a remote";
        new AlertDialog.Builder(this).setTitle("Choose your remote").setItems(names,(d,i)->{if(i==devices.size())addBrand();else select(devices.get(i));}).show();
    }
    private EditText input(String hint,boolean secret) {
        EditText field=new EditText(this);field.setHint(hint);field.setSingleLine();field.setTextColor(TEXT);field.setHintTextColor(MUTED);
        field.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT);
        field.setPadding(dp(12),dp(10),dp(12),dp(10));
        if(secret && android.os.Build.VERSION.SDK_INT>=26)field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        return field;
    }
    private void lgSetup() {
        LinearLayout form=column();form.setPadding(dp(22),dp(8),dp(22),0);
        form.addView(text("Same Wi-Fi as the TV. Accept the pairing prompt on the TV. Only models allowing local port 3000 are supported.",14,MUTED,false));
        EditText host=input("TV IP address, e.g. 192.168.1.20",false);form.addView(host);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Pair LG webOS").setView(form).setNegativeButton("Cancel",null).setNeutralButton("Discover",null).setPositiveButton("Pair",null).create();
        dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {new DeviceEndpoint(TvPlatform.LG_WEBOS,host.getText().toString().trim(),"");JSONObject device=baseDevice("LG","lg","LG webOS · "+host.getText().toString().trim());device.put("host",host.getText().toString().trim());dialog.dismiss();saveDevice(device);}
            catch(Exception e){host.setError(e.getMessage());}
        });dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{dialog.dismiss();discoverLg();});});dialog.show();
    }
    private void discoverLg() {
        message("Looking for LG TVs on this Wi-Fi…");
        worker.execute(()->{
            WifiManager.MulticastLock lock=null;
            try {
                WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
                if(wifi!=null){lock=wifi.createMulticastLock("palm-discovery");lock.setReferenceCounted(false);lock.acquire();}
                List<DiscoveredDevice> found=SsdpDiscovery.discover(3500);
                List<DiscoveredDevice> lgs=new ArrayList<>();for(DiscoveredDevice d:found)if(d.getPlatform()==TvPlatform.LG_WEBOS)lgs.add(d);
                ui(()->{
                    if(lgs.isEmpty()){message("No supported LG TV discovered. Try its IP address manually.");lgSetup();return;}
                    String[] names=new String[lgs.size()];for(int i=0;i<lgs.size();i++)names[i]=lgs.get(i).getName()+" · "+lgs.get(i).getHost();
                    new AlertDialog.Builder(this).setTitle("LG TVs on your network").setItems(names,(dialog,index)->{
                        try {JSONObject device=baseDevice("LG","lg",lgs.get(index).getName());device.put("host",lgs.get(index).getHost());saveDevice(device);}catch(Exception e){message(e.getMessage());}
                    }).setNegativeButton("Cancel",null).show();
                });
            }catch(Exception e){ui(()->message("Discovery failed. Enter the TV IP manually."));}
            finally{if(lock!=null&&lock.isHeld())lock.release();}
        });
    }
    private void smartThingsSetup() {
        LinearLayout form=column();form.setPadding(dp(22),dp(8),dp(22),0);
        form.addView(text("Optional manufacturer authorization. Use a SmartThings personal access token with device read and execute permissions. Requires your existing Samsung account; no Palm registration. Tokens may expire after 24 hours.",14,MUTED,false));
        EditText token=input("SmartThings access token",true);form.addView(token);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connect Samsung SmartThings").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Find TVs",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=token.getText().toString().trim();if(value.isEmpty()){token.setError("Enter your token, or use infrared instead.");return;}
            token.setText("");dialog.dismiss();message("Reading authorized SmartThings devices…");
            worker.execute(()->{
                try {
                    List<SmartThingsClient.Device> found=new SmartThingsClient(value).listDevices();
                    ui(()->{
                        if(found.isEmpty()){message("No controllable devices were returned. Check device access and token permissions.");return;}
                        String[] labels=new String[found.size()];for(int i=0;i<found.size();i++)labels[i]=found.get(i).getLabel();
                        new AlertDialog.Builder(this).setTitle("Select your Samsung TV").setItems(labels,(d2,index)->{
                            try {SmartThingsClient.Device match=found.get(index);JSONObject device=baseDevice("Samsung","smartthings",match.getLabel());device.put("cloudId",match.getId());store.putCredential(device.getString("id"),value);saveDevice(device);}
                            catch(Exception e){message("Could not securely save this device: "+e.getMessage());}
                        }).setNegativeButton("Cancel",null).show();
                    });
                }catch(Exception e){ui(()->message(e.getMessage()));}
            });
        }));dialog.show();
    }
    private void keypad() {
        if(cloud!=null && cloudDevice!=null) {
            if(!cloudDevice.getCapabilities().contains("tvChannel")){message("This TV does not publish channel selection.");return;}
            EditText channel=input("Channel number, e.g. 101",false);
            channel.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
            final SmartThingsClient client=cloud;final SmartThingsClient.Device device=cloudDevice;
            new AlertDialog.Builder(this).setTitle("Choose a channel").setView(channel)
                .setNegativeButton("Cancel",null).setPositiveButton("Tune",(d,w)->runCloudAction(()->client.setChannel(device,channel.getText().toString().trim()))).show();
            return;
        }
        LinearLayout pad=column();pad.setPadding(dp(14),dp(10),dp(14),dp(10));
        for(int r=0;r<3;r++){LinearLayout line=row();for(int c=1;c<=3;c++){String n=String.valueOf(r*3+c);command(line,n,"digit_"+n,false);}pad.addView(line);}
        LinearLayout line=row();spacer(line);command(line,"0","digit_0",false);spacer(line);pad.addView(line);
        new AlertDialog.Builder(this).setTitle("Number pad").setView(pad).setPositiveButton("Done",null).show();refreshButtons();
    }
    private interface CloudAction {void execute() throws Exception;}
    private void runCloudAction(CloudAction action) {
        if(busy||!connected)return;
        busy=true;refreshButtons();final long generation=connectionGeneration;
        worker.execute(()->{
            try{action.execute();ui(()->{if(generation==connectionGeneration)updateStatus("SmartThings command accepted",true);});}
            catch(Exception e){ui(()->{if(generation==connectionGeneration)message(e.getMessage());});}
            finally{ui(()->{if(generation==connectionGeneration){busy=false;refreshButtons();}});}
        });
    }
    private void cloudInput() {
        final SmartThingsClient client=cloud;final SmartThingsClient.Device device=cloudDevice;
        final long generation=connectionGeneration;busy=true;refreshButtons();
        worker.execute(()->{
            try{
                List<String> inputs=client.inputSources(device);
                ui(()->{
                    if(generation!=connectionGeneration)return;
                    if(inputs.isEmpty()){message("No input sources were published by this TV.");return;}
                    new AlertDialog.Builder(this).setTitle("TV input").setItems(inputs.toArray(new String[0]),
                        (d,index)->{if(generation==connectionGeneration)runCloudAction(()->client.setInputSource(device,inputs.get(index)));}).setNegativeButton("Cancel",null).show();
                });
            }catch(Exception e){ui(()->{if(generation==connectionGeneration)message(e.getMessage());});}
            finally{ui(()->{if(generation==connectionGeneration){busy=false;refreshButtons();}});}
        });
    }
    private void message(String value) {Toast.makeText(this,value==null?"Connection failed":value,Toast.LENGTH_LONG).show();}
    private void ui(Runnable task) {if(!destroyed)runOnUiThread(()->{if(!destroyed)task.run();});}
    @Override protected void onDestroy(){destroyed=true;connectionGeneration++;if(network!=null)network.dispose();worker.shutdownNow();hardwareWorker.shutdownNow();super.onDestroy();}
}
