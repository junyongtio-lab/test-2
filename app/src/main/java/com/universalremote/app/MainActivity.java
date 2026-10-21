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
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/** Account-free, local-first controller. A command is successful only after the transport accepts it. */
public final class MainActivity extends Activity {
    private static final int BG=0xff10141f, CARD=0xff1a2130, EDGE=0xff2c3548,
        TEXT=0xfff5f7fc, MUTED=0xffa8b3c8, MINT=0xff91e5d0, LILAC=0xffbcb3ff;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService hardwareWorker = Executors.newSingleThreadExecutor();
    private IrHardware.Result irHardware = IrHardware.checking();
    private DeviceStore store;
    private IrCatalog infrared;
    private PanasonicAcEncoder panasonic;
    private final List<JSONObject> devices = new ArrayList<>();
    private JSONObject selected;
    private NetworkRemote network;
    private SmartThingsClient cloud;
    private SmartThingsClient.Device cloudDevice;
    private LinearLayout root, content, nav;
    private TextView statusText;
    private TextView irHardwareTitle, irHardwareDetail;
    private Button irHardwareButton;
    private String currentPage="Home", status="Choose a device to get started", notice="", irProfileError="";
    private boolean connected, busy, hardwareChecking, irCatalogFailed, panasonicFailed;
    private volatile boolean destroyed;
    private volatile long connectionGeneration;
    private long renderedControlsGeneration;
    private final Map<View,String> commandButtons = new HashMap<>();
    private final List<View> panasonicSendButtons = new ArrayList<>();
    private final List<View> panasonicEditors = new ArrayList<>();
    private PanasonicAcEncoder.State panasonicDraft;
    private String panasonicDraftDeviceId="";

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
        // AC parameter loading is independent: a bad AC asset must not disable working Astro/TV remotes.
        worker.execute(()->{
            try {
                PanasonicAcEncoder loaded=new PanasonicAcEncoder(this);
                ui(()->{panasonic=loaded;if(selectedUsesPanasonic())connect();render();});
            } catch(Exception error) {
                ui(()->{panasonicFailed=true;if(selectedUsesPanasonic()){updateIrRemoteAvailability();render();}});
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
        if(selected!=null)details+="\n\nSelected remote: "+selected.optString("name")
            +"\nSource keyset: "+selected.optString("sourceModelId",selected.optString("profile"));
        new AlertDialog.Builder(this).setTitle(irHardware.title).setMessage(details)
            .setPositiveButton("Done",null).setNeutralButton("Check again",(dialog,which)->checkIrHardware(true)).show();
    }
    private boolean selectedUsesIr() {
        return selected!=null&&selected.optString("mode").equals("ir");
    }
    private boolean selectedUsesPanasonic() {
        return selected!=null&&selected.optString("mode").equals("panasonic_ac");
    }
    private void updateIrRemoteAvailability() {
        if(selectedUsesPanasonic()) {
            if(panasonic==null)updateStatus(panasonicFailed?"Panasonic AC definitions could not load.":"Panasonic AC definitions are loading…",false);
            else if(panasonic.find(selected.optString("profile"))==null)updateStatus("This Panasonic AC profile is unavailable. Choose a matching AC remote again.",false);
            else if(infrared==null)updateStatus(irCatalogFailed?"The infrared catalogue could not load. Restart the app to retry.":"The infrared transmitter service is loading…",false);
            else updateStatus(irHardware.title+(irHardware.hasEmitter?" · appliance response unverified":""),irHardware.hasEmitter);
            return;
        }
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
    private ImageView glyph(String kind,int color,int size) {
        ImageView icon=new ImageView(this);icon.setImageDrawable(new PalmGlyph(kind,color));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return icon;
    }
    private void weighted(LinearLayout into,View view,int height) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),1); p.setMargins(dp(4),dp(4),dp(4),dp(4)); into.addView(view,p);
    }
    private void full(LinearLayout into,View view,int height) { into.addView(view,new LinearLayout.LayoutParams(-1,dp(height))); }
    private void render() {
        renderedControlsGeneration++;
        commandButtons.clear();panasonicSendButtons.clear();panasonicEditors.clear();statusText=null;
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
        LinearLayout brand=column(); brand.addView(text("P A L M   R E M O T E",11,MINT,true)); gap(brand,4);
        brand.addView(text(currentPage.equals("Home")?"A little more control.":currentPage.equals("Remote")?"Your remote":currentPage,25,TEXT,true));
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
        if (currentPage.equals("Home")) homePage();
        else if (currentPage.equals("Remote")) remotePage();
        else if (currentPage.equals("Devices")) devicesPage(); else helpPage();
        nav=row(); nav.setPadding(0,dp(8),0,dp(8));
        for (String page:new String[]{"Home","Remote","Devices","Help"}) {
            boolean active=currentPage.equals(page);
            LinearLayout tab=column();tab.setGravity(Gravity.CENTER);tab.setPadding(0,dp(7),0,dp(7));
            tab.setBackground(shape(active?CARD:BG,16));
            tab.addView(glyph(page.toLowerCase(Locale.ROOT),active?MINT:MUTED,22));gap(tab,4);
            tab.addView(text(page,11,active?MINT:MUTED,active));
            tab.setContentDescription(page+" tab"+(active?", selected":""));tab.setFocusable(true);
            tab.setOnClickListener(v->{currentPage=page;render();});weighted(nav,tab,62);
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
    private void section(String title,String detail) {
        gap(content,8);content.addView(text(title,19,TEXT,true));
        if(!detail.isEmpty()){gap(content,4);content.addView(text(detail,13,MUTED,false));}gap(content,12);
    }
    private void homePage() {
        LinearLayout welcome=card("One home. Your remotes.","No Palm account. No subscription. Choose an appliance to begin.");
        LinearLayout overview=row();
        TextView count=text(devices.size()+ (devices.size()==1?" saved remote":" saved remotes"),13,MINT,true);
        overview.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        TextView local=text("ON THIS PHONE",10,MUTED,true);overview.addView(local);gap(welcome,14);welcome.addView(overview);
        section("Add a remote","Choose a category, then find and test a matching keyset.");
        LinearLayout first=row();categoryTile(first,"tv","Television","IR or supported Wi-Fi",MINT);
        categoryTile(first,"set_top_box","Decoder","Recorded IR controls",LILAC);content.addView(first);
        LinearLayout second=row();categoryTile(second,"air_conditioner","Air conditioner","Recorded IR settings",0xffffcc97);
        categoryTile(second,"fan","Fan","Speed, swing & more",0xff9ccfff);content.addView(second);gap(content,14);
        if(!devices.isEmpty()) {
            section("Ready when you are","Tap a saved device to open its remote.");
            for(JSONObject device:devices)savedDeviceCard(device,false);
        }
        LinearLayout astro=card("Set up your Astro decoder","Five Astro remotes from Mi Remote’s regional catalogue are included. Test them with your decoder to find a match. Ultra Box, Ulti Box and NJOI model compatibility still needs confirmation on your device.");
        gap(astro,12);full(astro,action("Set up Astro",()->irSetup("Astro","set_top_box"),true),48);
        if(!notice.isEmpty())card("Notice",notice);
    }
    private void categoryTile(LinearLayout parent,String category,String title,String subtitle,int accent) {
        LinearLayout tile=column();tile.setPadding(dp(16),dp(18),dp(12),dp(15));tile.setBackground(shape(CARD,23));
        tile.addView(glyph(category,accent,34));gap(tile,14);tile.addView(text(title,15,TEXT,true));gap(tile,3);
        tile.addView(text(subtitle,11,MUTED,false));tile.setGravity(Gravity.TOP);
        tile.setContentDescription("Add "+title+" remote");tile.setFocusable(true);
        tile.setOnClickListener(v->startCategory(category));
        weighted(parent,tile,164);
    }
    private void savedDeviceCard(JSONObject device,boolean editable) {
        LinearLayout c=card("","");LinearLayout line=row();
        String category=device.optString("category","tv");line.addView(glyph(category,MINT,30));
        LinearLayout labels=column();labels.setPadding(dp(12),0,0,0);
        labels.addView(text(device.optString("name"),16,TEXT,true));gap(labels,3);
        labels.addView(text(device.optString("brand")+" · "+connectionLabel(device),12,MUTED,false));
        line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));line.addView(text("›",28,MINT,false));c.addView(line);
        c.setContentDescription("Open "+device.optString("name"));c.setFocusable(true);c.setOnClickListener(v->select(device));
        if(editable) {
            gap(c,12);LinearLayout actions=row();weighted(actions,action("Open",()->select(device),true),48);
            weighted(actions,action("Remove",()->new AlertDialog.Builder(this).setTitle("Remove remote?").setMessage(device.optString("name"))
                .setNegativeButton("Cancel",null).setPositiveButton("Remove",(dialog,which)->{
                    devices.remove(device);store.delete(device.optString("id"));store.save(devices);
                    if(selected==device){selected=devices.isEmpty()?null:devices.get(0);connect();}render();}).show(),false),48);c.addView(actions);
        }
    }
    private void remotePage() {
        if (selected==null) {
            LinearLayout c=card("TVs, decoders, ACs & fans.","Choose a matching remote from the Mi Remote IR catalogue. No Palm account, subscriptions or ads.");
            gap(c,16); full(c,action("Add your first remote",this::addBrand,true),54);
            card("The right connection", "Infrared requires a compatible Android phone and a matching Mi Remote keyset. Five Astro decoder remotes are bundled for testing. Samsung SmartThings and supported LG webOS Wi-Fi pairing are also available. PRISM+ is absent from this snapshot.");
            card("Built for your Android phone", "An APK cannot run on iPhone. Most Android phones also have no IR transmitter; check your phone before choosing infrared.");
            if (!notice.isEmpty()) card("Notice",notice);
            return;
        }
        if(selected.optString("mode").equals("ir")){recordedIrPage();return;}
        if(selectedUsesPanasonic()){panasonicAcPage();return;}
        if(!usesNetwork(selected)){legacyUnavailablePage();return;}
        LinearLayout device=card(selected.optString("name"), selected.optString("brand")+"  ·  "+connectionLabel(selected));
        statusText=text(status,13,connected?MINT:MUTED,false);gap(device,8);device.addView(statusText);
        statusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        gap(device,10);full(device,action("Switch device  ▾",this::pickDevice,false),44);
        LinearLayout top=row(); command(top,"Power","power",true);command(top,"Input","input",false); content.addView(top);
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
        switch(device.optString("mode")) { case "ir":return "Mi Remote infrared";case "panasonic_ac":return "Panasonic AC infrared";case "lg":return "Local Wi-Fi";case "smartthings":return "SmartThings cloud";default:return "Unavailable legacy remote"; }
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
        boolean acReady=selectedUsesPanasonic()&&panasonic!=null&&infrared!=null&&irHardware.hasEmitter&&!busy
            &&panasonic.find(selected.optString("profile"))!=null;
        for(View button:panasonicSendButtons){button.setEnabled(acReady);button.setAlpha(acReady?1f:.3f);}
        for(View editor:panasonicEditors)editor.setEnabled(!busy);
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
        if(selectedUsesPanasonic()){updateIrRemoteAvailability();return;}
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
        for(JSONObject device:devices)savedDeviceCard(device,true);
        full(content,action("+ Add a remote",this::addBrand,true),54);gap(content,12);
    }
    private void helpPage() {
        card("Mi Remote infrared catalogue", "Bundled IR signals include the published Mi GitHub catalogue, 604 additional TV/AC profiles recovered from the uploaded Mi Remote 7.2.4M database and five Astro keysets from its regional service. They work offline without a profile-import step. No Palm account, subscriptions, advertisements or analytics.");
        if(infrared!=null)card("Available remote keysets",infrared.profileCount()+" keysets across televisions, set-top boxes, air conditioners and fans. Search by the database brand, then try a recorded remote on your appliance. Source keyset IDs are not retail model numbers; a brand match alone does not establish compatibility.");
        card("Astro decoder setup", "Use Set up Astro on Home, then Find a working remote. Five actual Astro keysets are available. Test several buttons and confirm the decoder responds before saving. The source does not identify Ultra Box, Ulti Box or NJOI models, so those matches require testing. The earlier unverified Astro codes remain removed. PRISM+ is absent from the catalogue.");
        card("Recorded buttons", "Recorded remotes show the actual decoded command labels from their source data. Only captured signals are available in those remotes; missing buttons are not invented. Some source formats cannot be decoded and are excluded.");
        card("Panasonic AC settings", "Choose Air conditioner, then Panasonic and Find a Panasonic AC remote. Six supported parameter profiles can prepare complete messages for 16–30 °C, supported modes and available fan/swing settings. Test each candidate on your AC. Other Panasonic families and Mi Remote’s remaining AC formats are not covered by this encoder. Settings are requested values, not readings from the appliance; editing them sends nothing until you tap a send or power button.");
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
            startCategory(categories[index]);
        }).setNegativeButton("Cancel",null).show();
    }
    private void startCategory(String category) {
        if(category.equals("set_top_box")) {
            new AlertDialog.Builder(this).setTitle("Mi Remote decoder catalogue")
                .setMessage("Five Astro keysets are bundled for testing, with no JSON import needed. Choose Astro, then Find a working remote. Ultra Box, Ulti Box and NJOI model matches require confirmation on your decoder.")
                .setPositiveButton("Browse brands",(d,w)->categoryBrands(category)).setNegativeButton("Cancel",null).show();
        }else categoryBrands(category);
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
    private boolean ensurePanasonicDraft(JSONObject profile) {
        String identity=selected.optString("id")+"|"+selected.optString("profile");
        if(identity.equals(panasonicDraftDeviceId)&&panasonicDraft!=null)return true;
        panasonicDraftDeviceId=identity;
        try{panasonicDraft=PanasonicAcEncoder.defaults(profile);}catch(Exception error){panasonicDraft=null;return false;}
        try {
            JSONObject saved=selected.optJSONObject("requestedState");
            if(saved!=null) {
                PanasonicAcEncoder.State restored=PanasonicAcEncoder.State.fromJson(saved);
                PanasonicAcEncoder.encode(profile,restored,false);
                panasonicDraft=restored;
            }
        }catch(Exception ignored){/* Invalid saved requests become a safe draft, never an automatic transmission. */}
        return true;
    }
    private interface IndexChoice { void choose(int index); }
    private Spinner acSelector(LinearLayout parent,String heading,List<String> labels,int current,IndexChoice chosen) {
        final long generation=renderedControlsGeneration;
        parent.addView(text(heading,12,MUTED,true));gap(parent,4);
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0,Math.min(current,labels.size()-1)));
        spinner.setContentDescription(heading);parent.addView(spinner,new LinearLayout.LayoutParams(-1,dp(48)));
        panasonicEditors.add(spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id){
                if(!destroyed&&!busy&&generation==renderedControlsGeneration&&position>=0&&position<labels.size())chosen.choose(position);
            }
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
        gap(parent,12);return spinner;
    }
    private Button acSendButton(String label,Runnable send,boolean primary) {
        Button button=action(label,send,primary);panasonicSendButtons.add(button);return button;
    }
    private void panasonicAcPage() {
        deviceHeading();
        JSONObject profile=panasonic==null?null:panasonic.find(selected.optString("profile"));
        if(profile==null) {
            card("Panasonic AC definitions",panasonicFailed?"The bundled AC definitions could not load. Other remotes remain available.":"Loading the bundled AC definitions…");return;
        }
        if(!ensurePanasonicDraft(profile)){card("Panasonic AC profile unavailable","The saved AC definition could not be validated. Choose another profile.");return;}
        boolean toggle=profile.optBoolean("powerToggle");
        card("Requested settings", "These controls prepare a complete AC message. They do not read the air conditioner's current state. Nothing is sent until you tap a send or power button.");
        LinearLayout controls=card("Panasonic AC · "+profile.optString("model"),toggle?"Power uses a toggle signal; the actual on/off state is unknown.":"Requested power: "+(panasonicDraft.power?"On":"Off"));
        TextView temperature=text(panasonicDraft.temperature+" °C",34,MINT,true);temperature.setGravity(Gravity.CENTER);controls.addView(temperature);gap(controls,8);
        LinearLayout temperatureButtons=row();
        Button lower=action("Temperature −",()->{if(!busy&&panasonicDraft.temperature>16){panasonicDraft.temperature--;temperature.setText(panasonicDraft.temperature+" °C");}},false);
        Button higher=action("Temperature +",()->{if(!busy&&panasonicDraft.temperature<30){panasonicDraft.temperature++;temperature.setText(panasonicDraft.temperature+" °C");}},false);
        panasonicEditors.add(lower);panasonicEditors.add(higher);
        weighted(temperatureButtons,lower,52);weighted(temperatureButtons,higher,52);
        controls.addView(temperatureButtons);gap(controls,12);
        List<String> modes=new ArrayList<>();JSONArray sourceModes=profile.optJSONArray("modes");
        if(sourceModes!=null)for(int i=0;i<sourceModes.length();i++)modes.add(sourceModes.optString(i));
        if(!modes.isEmpty())acSelector(controls,"Mode",modes,modes.indexOf(panasonicDraft.mode),index->panasonicDraft.mode=modes.get(index));
        if(profile.optBoolean("supportsFan"))acSelector(controls,"Fan speed",Arrays.asList("Auto","Low","Medium","High"),panasonicDraft.fan,index->panasonicDraft.fan=index);
        if(profile.optBoolean("supportsSwing"))acSelector(controls,"Vertical swing",Arrays.asList("Auto swing","Position 1","Position 2","Position 3","Position 4","Position 5"),panasonicDraft.swing,index->panasonicDraft.swing=index);
        full(controls,acSendButton("Send requested settings",()->sendPanasonic(panasonicDraft.copy(),false,"requested settings"),true),54);gap(controls,10);
        if(toggle) {
            full(controls,acSendButton("Send power toggle",()->sendPanasonic(panasonicDraft.copy(),true,"power toggle"),false),52);
        } else {
            LinearLayout power=row();
            weighted(power,acSendButton("Power on",()->{PanasonicAcEncoder.State next=panasonicDraft.copy();next.power=true;sendPanasonic(next,true,"power on");},false),52);
            weighted(power,acSendButton("Power off",()->{PanasonicAcEncoder.State next=panasonicDraft.copy();next.power=false;sendPanasonic(next,true,"power off");},false),52);
            controls.addView(power);
        }
        if(!profile.optBoolean("supportsFan")) {gap(controls,12);controls.addView(text("This source profile has no verified fan-speed mapping; its captured default is preserved.",12,MUTED,false));}
        gap(content,8);full(content,action("Find a different Panasonic remote",()->panasonicSetup(selected),false),48);
        gap(content,12);content.addView(text("Source keyset: "+profile.optString("model")+". Temperature, mode and supported fan/swing settings come from this profile's verified parameters; other Panasonic families may need a different remote.",12,MUTED,false));
        refreshButtons();
    }
    private void sendPanasonic(PanasonicAcEncoder.State requested,boolean powerButtonPress,String label) {
        if(!selectedUsesPanasonic()||panasonic==null||infrared==null||!irHardware.hasEmitter||busy){message("A detected built-in IR transmitter and a loaded Panasonic profile are required.");return;}
        final JSONObject device=selected,profile=panasonic.find(selected.optString("profile"));
        if(profile==null){message("Choose a supported Panasonic profile first.");return;}
        final IrCatalog transmitter=infrared;final long generation=connectionGeneration;
        final PanasonicAcEncoder.State snapshot=requested.copy();busy=true;refreshButtons();
        worker.execute(()->{
            try {
                if(destroyed||generation!=connectionGeneration||selected!=device)return;
                PanasonicAcEncoder.Signal signal=PanasonicAcEncoder.encode(profile,snapshot,powerButtonPress);
                if(destroyed||generation!=connectionGeneration||selected!=device)return;
                transmitter.transmitSignal(signal.carrierHz,signal.pattern);
                ui(()->{
                    if(generation!=connectionGeneration||selected!=device)return;
                    try {device.put("requestedState",snapshot.toJson());store.save(devices);panasonicDraft=snapshot.copy();}
                    catch(Exception ignored){message("IR was sent, but the requested settings could not be saved.");}
                    updateStatus("IR sent · "+label+". Confirm the AC responds.",true);render();
                });
            }catch(Exception error){ui(()->{if(generation==connectionGeneration){updateStatus("Signal could not be sent: "+error.getMessage(),irHardware.hasEmitter);message(error.getMessage());}});}
            finally{ui(()->{if(generation==connectionGeneration){busy=false;refreshButtons();}});}
        });
    }
    private void panasonicSetup() { panasonicSetup(null); }
    private void panasonicSetup(JSONObject replacement) {
        final PanasonicAcEncoder encoder=panasonic;
        if(encoder==null){message(panasonicFailed?"Panasonic AC definitions could not load. Recorded AC remotes remain available.":"Panasonic AC definitions are loading. Try again shortly.");return;}
        final List<JSONObject> profiles=encoder.profiles();
        if(profiles.isEmpty()){message("No verified Panasonic AC parameter profiles are available.");return;}
        int initial=0;if(replacement!=null)for(int i=0;i<profiles.size();i++)if(profiles.get(i).optString("id").equals(replacement.optString("profile")))initial=i;
        final int[] current={initial};final boolean[] sending={false},signalSent={false};
        final java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean(true);
        LinearLayout body=column();body.setPadding(dp(20),dp(8),dp(20),dp(12));
        TextView position=text("",17,TEXT,true);body.addView(position);gap(body,10);
        body.addView(text("Try each candidate while aiming at the AC. The test requests Cool at 24 °C. Only you can confirm whether the appliance responds. These source IDs are not retail model numbers.",13,MUTED,false));gap(body,12);
        TextView result=text("",13,MUTED,false);result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(result);gap(body,12);
        Button test=action("Test AC signal",()->{},true);full(body,test,60);gap(body,12);
        LinearLayout pages=row();Button previous=action("Previous",()->{},false),next=action("Next remote",()->{},false);weighted(pages,previous,48);weighted(pages,next,48);body.addView(pages);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Find a Panasonic AC remote").setView(body)
            .setNegativeButton("Close",null).setNeutralButton("Save preview",null).setPositiveButton("Use this remote",null).create();
        Runnable refresh=()->{
            JSONObject profile=profiles.get(current[0]);
            position.setText("AC remote "+(current[0]+1)+" of "+profiles.size()+" · "+profile.optString("model"));
            test.setText(profile.optBoolean("powerToggle")?"Test power toggle · Cool 24 °C":"Test power on · Cool 24 °C");
            test.setEnabled(!sending[0]&&irHardware.hasEmitter&&infrared!=null);
            result.setText(irHardware.hasEmitter?"Tap the test button once, then check the AC yourself.":irHardware.title+". You can save a preview; infrared sending remains disabled.");
            previous.setEnabled(!sending[0]&&current[0]>0);next.setEnabled(!sending[0]&&current[0]<profiles.size()-1);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!sending[0]&&signalSent[0]);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!sending[0]);
        };
        previous.setOnClickListener(v->{if(!sending[0]&&current[0]>0){current[0]--;signalSent[0]=false;refresh.run();}});
        next.setOnClickListener(v->{if(!sending[0]&&current[0]<profiles.size()-1){current[0]++;signalSent[0]=false;refresh.run();}});
        test.setOnClickListener(v->{
            if(sending[0]||!irHardware.hasEmitter||infrared==null)return;
            final int tested=current[0];final JSONObject profile=profiles.get(tested);final IrCatalog transmitter=infrared;
            sending[0]=true;test.setEnabled(false);previous.setEnabled(false);next.setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            result.setText("Sending the requested AC test message…");
            worker.execute(()->{
                boolean accepted=false;String outcome;
                try {
                    if(destroyed||!active.get())return;
                    PanasonicAcEncoder.State requested=PanasonicAcEncoder.defaults(profile);
                    PanasonicAcEncoder.Signal signal=PanasonicAcEncoder.encode(profile,requested,true);
                    if(destroyed||!active.get())return;
                    transmitter.transmitSignal(signal.carrierHz,signal.pattern);accepted=true;
                    outcome="IR sent. Did the AC respond? Confirm only if it did, or try the next remote.";
                }catch(Exception error){outcome="Signal could not be sent: "+error.getMessage();}
                final boolean sent=accepted;final String message=outcome;
                ui(()->{
                    if(!active.get()||tested!=current[0])return;
                    sending[0]=false;signalSent[0]|=sent;refresh.run();result.setText(message);
                });
            });
        });
        dialog.setOnDismissListener(d->active.set(false));
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                if(sending[0])return;JSONObject profile=profiles.get(current[0]);
                new AlertDialog.Builder(this).setTitle("Save an untested preview?")
                    .setMessage("This saves the selected controls without claiming they work with your AC. No infrared signal is sent by saving. Sending still requires a detected built-in emitter.")
                    .setNegativeButton("Keep testing",null).setPositiveButton("Save preview",(confirm,which)->{dialog.dismiss();savePanasonic(profile,false,replacement);}).show();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(sending[0]||!signalSent[0])return;JSONObject profile=profiles.get(current[0]);
                new AlertDialog.Builder(this).setTitle("Did your AC respond?")
                    .setMessage("Save this candidate as tested only if the AC responded correctly. Check other settings after saving; Palm cannot read the appliance's state.")
                    .setNegativeButton("Keep testing",null).setPositiveButton("Yes, save remote",(confirm,which)->{dialog.dismiss();savePanasonic(profile,true,replacement);}).show();
            });refresh.run();
        });dialog.show();
    }
    private void savePanasonic(JSONObject profile,boolean responseConfirmed,JSONObject replacement) {
        try {
            JSONObject device=replacement==null?baseDevice("Panasonic","panasonic_ac","Panasonic AC · "+profile.getString("model")):new JSONObject(replacement.toString());
            device.put("name","Panasonic AC · "+profile.getString("model")).put("brand","Panasonic").put("mode","panasonic_ac")
                .put("profile",profile.getString("id")).put("category","air_conditioner").put("sourceModelId",profile.getString("model"))
                .put("responseConfirmed",responseConfirmed).put("requestedState",PanasonicAcEncoder.defaults(profile).toJson());
            panasonicDraft=null;panasonicDraftDeviceId="";saveOrReplaceDevice(device,replacement);
        }catch(Exception error){message("Could not save the Panasonic remote: "+error.getMessage());}
    }
    private void recordedIrPage() {
        deviceHeading();
        JSONObject profile=infrared==null?null:infrared.find(selected.optString("profile"));
        if(profile==null) {
            card("Remote unavailable",infrared==null?"The Mi Remote catalogue is loading…":"Add this remote again from the Mi Remote catalogue.");
        }else {
            String category=profile.optString("category","tv");
            JSONObject commands=profile.optJSONObject("commands");
            if(commands==null)card("Signals",irProfileError.isEmpty()?"Loading this keyset’s recorded signals…":irProfileError);
            else {
                List<String> keys=new ArrayList<>();Iterator<String> names=commands.keys();while(names.hasNext())keys.add(names.next());
                Collections.sort(keys);RecordedControls controls=new RecordedControls(keys);
                String guidance="Aim the phone at your appliance. IR sent means the phone transmitted; confirm the appliance responds.";
                if(category.equals("air_conditioner"))guidance+=" Each captured setting may change several AC settings at once.";
                content.addView(text(guidance,12,MUTED,false));gap(content,12);
                recordedRow(controls,new String[]{"power","input"},new String[]{"Power","Input"},"power");
                boolean navigation=false;for(String role:new String[]{"up","down","left","right","ok"})if(controls.key(role)!=null)navigation=true;
                if(navigation) {
                    gap(content,10);LinearLayout pad=column();pad.setPadding(dp(12),dp(12),dp(12),dp(12));pad.setBackground(shape(CARD,32));
                    recordedPadRow(pad,controls,new String[]{null,"up",null},new String[]{"","▲",""});
                    recordedPadRow(pad,controls,new String[]{"left","ok","right"},new String[]{"◀","OK","▶"});
                    recordedPadRow(pad,controls,new String[]{null,"down",null},new String[]{"","▼",""});
                    content.addView(pad);gap(content,8);
                }
                recordedRow(controls,new String[]{"back","home","menu"},new String[]{"Back","Home","Menu"},"");
                if(controls.key("volume_up")!=null||controls.key("volume_down")!=null||controls.key("channel_up")!=null||controls.key("channel_down")!=null) {
                    gap(content,10);LinearLayout adjustments=row();
                    recordedRocker(adjustments,controls,"VOLUME","volume_up","volume_down");
                    recordedRocker(adjustments,controls,"CHANNEL","channel_up","channel_down");
                    content.addView(adjustments);
                }
                if(controls.key("temperature_up")!=null||controls.key("temperature_down")!=null) {
                    gap(content,10);content.addView(text("RECORDED TEMPERATURE CONTROLS",11,MUTED,true));gap(content,6);
                    recordedRow(controls,new String[]{"temperature_down","temperature_up"},new String[]{"Temperature −","Temperature +"},"");
                }
                recordedRow(controls,new String[]{"mute","guide","info"},new String[]{"Mute","Guide","Info"},"");
                recordedRow(controls,new String[]{"mode","fan_speed","timer"},new String[]{"Mode","Fan speed","Timer"},"");
                gap(content,12);
                if(!controls.digits().isEmpty()) {
                    full(content,action("123  Number pad",()->showRecordedCommands("Number pad",controls.digits(),commands),false),54);gap(content,8);
                }
                if(!controls.remaining.isEmpty()) {
                    full(content,action("More commands  ·  "+controls.remaining.size(),()->showRecordedCommands("More recorded commands",controls.remaining,commands),false),54);gap(content,8);
                }
                full(content,action("All recorded buttons  ·  "+keys.size(),()->showRecordedCommands("All recorded buttons",keys,commands),false),48);
                gap(content,14);content.addView(text("Source keyset: "+profile.optString("model",profile.optString("id")),11,MUTED,false));
                if(keys.isEmpty())card("No decoded buttons","This keyset has no available commands. Choose another matching keyset.");
            }
        }
        gap(content,14);full(content,action("Reload remote",this::connect,false),48);
        if(profile!=null) {
            gap(content,8);full(content,action("Find a working remote",()->retestIr(selected),false),48);
        }
        if(!notice.isEmpty()){gap(content,10);content.addView(text(notice,13,MUTED,false));}
        refreshButtons();
    }
    private Button recordedKey(String label,String sourceKey,boolean primary) {
        Button button=key(label,sourceKey,primary);
        button.setContentDescription(label+", recorded source key "+sourceKey);return button;
    }
    private void recordedRow(RecordedControls controls,String[] roles,String[] labels,String primary) {
        LinearLayout line=row();int count=0;
        for(int i=0;i<roles.length;i++) {
            String code=controls.key(roles[i]);if(code==null)continue;
            weighted(line,recordedKey(labels[i],code,roles[i].equals(primary)),58);count++;
        }
        if(count>0)content.addView(line);
    }
    private void recordedPadRow(LinearLayout pad,RecordedControls controls,String[] roles,String[] labels) {
        LinearLayout line=row();
        for(int i=0;i<roles.length;i++) {
            String code=roles[i]==null?null:controls.key(roles[i]);
            if(code==null)spacer(line);else weighted(line,recordedKey(labels[i],code,"ok".equals(roles[i])),64);
        }
        pad.addView(line);
    }
    private void recordedRocker(LinearLayout parent,RecordedControls controls,String heading,String plus,String minus) {
        if(controls.key(plus)==null&&controls.key(minus)==null)return;
        LinearLayout rocker=column();rocker.setGravity(Gravity.CENTER);rocker.setPadding(dp(8),dp(12),dp(8),dp(8));rocker.setBackground(shape(CARD,24));
        TextView title=text(heading,11,MUTED,true);title.setGravity(Gravity.CENTER);rocker.addView(title);gap(rocker,8);
        if(controls.key(plus)!=null){Button button=recordedKey("+",controls.key(plus),false);button.setContentDescription(heading.equals("VOLUME")?"Volume up":"Channel up");full(rocker,button,54);}
        gap(rocker,4);if(controls.key(minus)!=null){Button button=recordedKey("−",controls.key(minus),false);button.setContentDescription(heading.equals("VOLUME")?"Volume down":"Channel down");full(rocker,button,54);}
        weighted(parent,rocker,158);
    }
    private void showRecordedCommands(String title,List<String> codes,JSONObject commands) {
        LinearLayout body=column();body.setPadding(dp(16),0,dp(16),dp(12));
        TextView note=text("Only recorded source signals are shown. A transmission does not confirm an appliance response.",12,MUTED,false);body.addView(note);gap(body,10);
        ScrollView scroll=new ScrollView(this);LinearLayout grid=column();scroll.addView(grid);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(350)));
        List<View> transientButtons=new ArrayList<>();LinearLayout line=null;
        for(int i=0;i<codes.size();i++) {
            if(i%2==0){line=row();grid.addView(line);}
            String code=codes.get(i);JSONObject signal=commands.optJSONObject(code);
            String label=signal==null?code:signal.optString("label",code);
            Button button=recordedKey(label,code,false);button.setSingleLine(false);button.setPadding(dp(8),dp(10),dp(8),dp(10));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(3),dp(3),dp(3),dp(3));
            line.addView(button,params);transientButtons.add(button);
        }
        if(codes.size()%2==1)line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(body).setPositiveButton("Done",null).create();
        dialog.setOnDismissListener(d->{for(View button:transientButtons)commandButtons.remove(button);});dialog.show();refreshButtons();
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
        if(brand.equalsIgnoreCase("Panasonic")&&category.equals("air_conditioner")) {
            options.add(new Choice("Find a Panasonic AC remote · Temperature and mode controls",this::panasonicSetup));
        }
        if(!profiles.isEmpty())options.add(new Choice("Find a working remote · Guided testing",()->matchIrProfiles(brand,profiles)));
        for(int index=0;index<profiles.size();index++) {
            JSONObject profile=profiles.get(index);
            final int candidate=index;
            final String remoteName="Remote "+(index+1);
            String label=remoteName;
            if(profile.has("commandCount"))label+=" · "+profile.optInt("commandCount")+" recorded buttons";
            label+=" · "+profile.optString("model");
            options.add(new Choice(label,()->{
                if(!irHardware.hasEmitter) {
                    new AlertDialog.Builder(this).setTitle(irHardware.title)
                        .setMessage("You can save this Mi Remote keyset. IR sending stays disabled until a built-in emitter is detected. Tap Check IR above the page for phone hardware details. A matching appliance model is required.")
                        .setPositiveButton("Save anyway",(d,w)->saveIr(brand,profile,remoteName)).setNegativeButton("Cancel",null).show();
                }else matchIrProfiles(brand,profiles,candidate,null);
            }));
        }
        if(options.isEmpty()){message("No decoded Mi Remote keysets are available for this brand and category.");return;}
        choices(brand+" · Mi Remote keyset",options);
    }
    private void matchIrProfiles(String brand,List<JSONObject> profiles) {
        matchIrProfiles(brand,profiles,0,null);
    }
    private void retestIr(JSONObject device) {
        if(infrared==null||device==null)return;
        List<JSONObject> profiles=infrared.forBrand(device.optString("brand"),device.optString("category","tv"));
        // Re-testing starts with the source-prioritized candidates, including after an
        // older build saved the last candidate as its former "Remote 1".
        matchIrProfiles(device.optString("brand"),profiles,0,device);
    }
    private void matchIrProfiles(String brand,List<JSONObject> profiles,int startIndex,JSONObject replacement) {
        final IrCatalog catalogue=infrared;
        if(catalogue==null||profiles.isEmpty())return;
        LinearLayout body=column();body.setPadding(dp(20),dp(8),dp(20),dp(12));
        TextView position=text("",17,TEXT,true);body.addView(position);gap(body,8);
        TextView explanation=text("Test one recorded button while aiming at the appliance. A keyset is a source capture, not a verified model match. Save only the remote you choose.",13,MUTED,false);
        body.addView(explanation);gap(body,12);
        Spinner command=new Spinner(this);body.addView(command,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView result=text("Loading signals…",13,MUTED,false);result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        gap(body,8);body.addView(result);gap(body,12);
        Button test=action("Send test signal",()->{},true);full(body,test,54);gap(body,12);
        LinearLayout paging=row();Button previous=action("Previous",()->{},false),next=action("Next remote",()->{},false);
        weighted(paging,previous,48);weighted(paging,next,48);body.addView(paging);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(brand+" · Find your remote").setView(body)
            .setNegativeButton("Close",null).setPositiveButton("Use this remote",null).create();
        final int[] current={Math.max(0,Math.min(startIndex,profiles.size()-1))};final boolean[] sending={false},signalSent={false};
        final List<String> codes=new ArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger request=new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean(true);
        Runnable[] load=new Runnable[1];
        load[0]=()->{
            int token=request.incrementAndGet();JSONObject metadata=profiles.get(current[0]);signalSent[0]=false;
            position.setText("Remote "+(current[0]+1)+" of "+profiles.size()+" · "+metadata.optString("model"));
            result.setText("Loading recorded signals…");test.setEnabled(false);command.setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            previous.setEnabled(current[0]>0);next.setEnabled(current[0]<profiles.size()-1);codes.clear();
            worker.execute(()->{
                try {
                    if(!active.get()||token!=request.get())return;
                    JSONObject loaded=catalogue.loadProfile(metadata.optString("id"));
                    JSONObject signals=loaded.getJSONObject("commands");
                    List<String> found=new ArrayList<>();Iterator<String> keys=signals.keys();while(keys.hasNext())found.add(keys.next());Collections.sort(found);
                    List<String> labels=new ArrayList<>();for(String code:found)labels.add(signals.getJSONObject(code).optString("label",code));
                    RecordedControls controls=new RecordedControls(found);String preferred=controls.key("power");if(preferred==null)preferred=controls.key("mute");
                    final int initial=preferred==null?0:found.indexOf(preferred);
                    ui(()->{
                        if(!active.get()||token!=request.get())return;
                        codes.addAll(found);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);command.setAdapter(adapter);
                        if(!codes.isEmpty())command.setSelection(initial);
                        command.setEnabled(!codes.isEmpty());test.setEnabled(irHardware.hasEmitter&&!codes.isEmpty());
                        result.setText(irHardware.hasEmitter?"Choose a recorded button. Tap Send test signal, then check the appliance yourself.":irHardware.title+". You can browse here. To save without testing, choose a remote from the keyset list.");
                    });
                }catch(Exception error){ui(()->{if(active.get()&&token==request.get())result.setText("Could not load this keyset: "+error.getMessage());});}
            });
        };
        previous.setOnClickListener(v->{if(!sending[0]&&current[0]>0){current[0]--;load[0].run();}});
        next.setOnClickListener(v->{if(!sending[0]&&current[0]<profiles.size()-1){current[0]++;load[0].run();}});
        test.setOnClickListener(v->{
            if(sending[0]||!irHardware.hasEmitter||codes.isEmpty())return;
            int selection=command.getSelectedItemPosition();if(selection<0||selection>=codes.size())return;
            String sourceKey=codes.get(selection);JSONObject metadata=profiles.get(current[0]);int token=request.get();
            sending[0]=true;test.setEnabled(false);previous.setEnabled(false);next.setEnabled(false);command.setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);result.setText("Sending one recorded signal…");
            worker.execute(()->{
                String outcome;boolean accepted=false;
                try {
                    if(destroyed||!active.get())return;
                    catalogue.transmit(metadata.optString("id"),sourceKey);
                    accepted=true;
                    outcome="IR sent. Did the appliance respond? Try another button to check, or choose the next remote. Palm cannot detect the response.";
                }catch(Exception error){outcome="Signal could not be sent: "+error.getMessage();}
                final String text=outcome;final boolean transmitted=accepted;ui(()->{
                    if(!active.get()||token!=request.get())return;
                    sending[0]=false;signalSent[0]|=transmitted;result.setText(text);test.setEnabled(irHardware.hasEmitter);command.setEnabled(true);
                    previous.setEnabled(current[0]>0);next.setEnabled(current[0]<profiles.size()-1);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(signalSent[0]);
                });
            });
        });
        dialog.setOnDismissListener(d->{
            active.set(false);request.incrementAndGet();
            // Browsing keysets shares the bounded cache with the selected remote. Restore it
            // after queued wizard reads finish; connect() retains the device-generation guard.
            final JSONObject originalSelection=selected;
            if(!destroyed)worker.execute(()->ui(()->{
                if(catalogue==infrared&&selected==originalSelection&&selectedUsesIr()&&!catalogue.isLoaded(selected.optString("profile")))connect();
            }));
        });
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(sending[0]||!signalSent[0])return;JSONObject chosen=profiles.get(current[0]);String name="Remote "+(current[0]+1);
                new AlertDialog.Builder(this).setTitle("Did your appliance respond?")
                    .setMessage("Save this keyset only if the appliance responded correctly to your test. Palm cannot detect that response. Other buttons may still need testing.")
                    .setNegativeButton("Keep testing",null).setPositiveButton("Yes, save remote",(confirm,which)->{dialog.dismiss();saveIr(brand,chosen,name,true,replacement);}).show();
            });load[0].run();
        });dialog.show();
    }
    private void saveIr(String brand,JSONObject profile,String remoteName) {
        saveIr(brand,profile,remoteName,false,null);
    }
    private void saveIr(String brand,JSONObject profile,String remoteName,boolean responseConfirmed,JSONObject replacement) {
        try {
            JSONObject device=replacement==null?baseDevice(brand,"ir",brand+" · "+remoteName):new JSONObject(replacement.toString());
            device.put("name",brand+" · "+remoteName).put("brand",brand).put("mode","ir").put("responseConfirmed",responseConfirmed);
            device.put("profile",profile.getString("id"));device.put("category",profile.optString("category","tv"));
            JSONObject source=profile.optJSONObject("source");if(source!=null)device.put("sourceModelId",source.optString("modelId"));
            saveOrReplaceDevice(device,replacement);
        }catch(Exception e){message(e.getMessage());}
    }
    private JSONObject baseDevice(String brand,String mode,String name)throws Exception {
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("brand",brand).put("mode",mode).put("name",name).put("category","tv");
    }
    private void saveDevice(JSONObject device) {devices.add(device);store.save(devices);select(device);}
    private void saveOrReplaceDevice(JSONObject device,JSONObject replacement) {
        if(replacement==null){saveDevice(device);return;}
        for(int i=0;i<devices.size();i++)if(devices.get(i).optString("id").equals(replacement.optString("id"))){devices.set(i,device);store.save(devices);select(device);return;}
        // The original entry may have been removed while the wizard was open.
        saveDevice(device);
    }
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
