package com.universalremote.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import com.universalremote.app.ac.AcEngine;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

/** Account-free, local-first controller. A command is successful only after the transport accepts it. */
public final class MainActivity extends Activity {
    private static final int BG=0xff0a111e, CARD=0xff141f30, EDGE=0xff263449,
        TEXT=0xfff2f5fa, MUTED=0xff9aabc0, MINT=0xff72e5c3;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private DeviceStore store;
    private IrCatalog infrared;
    private final List<JSONObject> devices = new ArrayList<>();
    private JSONObject selected;
    private NetworkRemote network;
    private SmartThingsClient cloud;
    private SmartThingsClient.Device cloudDevice;
    private LinearLayout root, content, nav;
    private TextView statusText;
    private String currentPage="Remote", status="Choose a device to get started", notice="";
    private boolean connected, busy, destroyed;
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
                ui(()->{infrared=loaded;render();if(selected!=null)connect();});
            } catch(Exception error) {
                ui(()->{notice="IR profiles could not load: "+error.getMessage();render();if(selected!=null)connect();});
            }
        });
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
        commandButtons.clear();
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
            LinearLayout c=card("TVs, decoders & air conditioners.","Samsung, LG, Panasonic, Hisense, PRISM+, Daewoo and Astro. Add an air conditioner from the searchable IR catalogue. No account, subscriptions or ads.");
            gap(c,16); full(c,action("Add your first remote",this::addBrand,true),54);
            card("The right connection", "Samsung: official SmartThings API. LG: supported webOS Wi-Fi pairing. Infrared: a compatible Android phone and a matching remote profile. Supplied Astro Ultra Box profiles are built in; no phone JSON import is needed. Physical decoder compatibility is untested.");
            card("Built for your Android phone", "An APK cannot run on iPhone. Most Android phones also have no IR transmitter; check your phone before choosing infrared.");
            if (!notice.isEmpty()) card("Notice",notice);
            return;
        }
        if(selected.optString("mode").equals("ac")){acRemotePage();return;}
        if(selected.optString("category","tv").equals("air_conditioner")){capturedAcPage();return;}
        LinearLayout device=card(selected.optString("name"), selected.optString("brand")+"  ·  "+connectionLabel(selected));
        statusText=text(status,13,connected?MINT:MUTED,false);gap(device,8);device.addView(statusText);
        statusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        gap(device,10);full(device,action("Switch device  ▾",this::pickDevice,false),44);
        JSONObject irProfile=selected.optString("mode").equals("ir")&&infrared!=null?infrared.find(selected.optString("profile")):null;
        if(irProfile!=null&&!irProfile.optString("notice").trim().isEmpty())card("Profile guidance",irProfile.optString("notice"));
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
        if(hasIrCommand("record")) {LinearLayout recording=row();command(recording,"Record","record",false);content.addView(recording);}
        LinearLayout colors=row();
        for(String color:new String[]{"red","green","yellow","blue"})if(hasIrCommand(color))
            command(colors,color.substring(0,1).toUpperCase(Locale.ROOT)+color.substring(1),color,false);
        if(colors.getChildCount()>0)content.addView(colors);
        LinearLayout guide=row();command(guide,"Guide","guide",false);guide.addView(action("123  Keypad",this::keypad,false),new LinearLayout.LayoutParams(0,dp(56),1));content.addView(guide);
        gap(content,14);
        full(content,action("Reconnect",this::connect,false),48);
        gap(content,14);
        content.addView(text(selected.optString("mode").equals("ir")?
            "Aim your phone at the device. Sending IR cannot confirm that the device responded. Profiles have not been tested on physical TVs or decoders here.":
            "Only buttons supported by this connection are enabled. Pairing and model support depend on your TV.",12,MUTED,false));
        if (!notice.isEmpty()) {gap(content,10);content.addView(text(notice,13,MUTED,false));}
        refreshButtons();
    }
    private String connectionLabel(JSONObject device) {
        switch(device.optString("mode")) { case "ir":return "Infrared"; case "ac":return "Stateful AC infrared";case "lg":return "Local Wi-Fi"; default:return "SmartThings cloud"; }
    }
    private void spacer(LinearLayout row) { row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(58),1)); }
    private void command(LinearLayout row,String label,String code,boolean primary) { weighted(row,key(label,code,primary),58); }
    private Button key(String label,String code,boolean primary) {
        Button b=action(label,()->send(code),primary); b.setContentDescription(labelFor(code));commandButtons.put(b,code);return b;
    }
    private String labelFor(String code) { return code.replace('_',' '); }
    private boolean hasIrCommand(String code) {
        return selected!=null&&selected.optString("mode").equals("ir")&&infrared!=null&&infrared.supports(selected.optString("profile"),code);
    }
    private RemoteCommand networkCommand(String code) {
        String name=code.equals("ok")?"SELECT":code.equals("input")?"SOURCE":code.toUpperCase(Locale.ROOT);
        try{return RemoteCommand.valueOf(name);}catch(Exception e){return null;}
    }
    private boolean supports(String code) {
        if (selected==null || !connected || busy) return false;
        if(code.equals("__ac_send"))return selected.optString("mode").equals("ac")&&AcEngine.isAvailable();
        if (selected.optString("mode").equals("ir")) return infrared!=null && infrared.supports(selected.optString("profile"),code);
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
                ui(()->{if(generation==connectionGeneration)updateStatus(device.optString("mode").equals("ir")?"IR sent · "+labelFor(command):"Command sent · "+labelFor(command),true);});
            }catch(Exception error){ui(()->{if(generation==connectionGeneration) {updateStatus(error.getMessage(),connected);message(error.getMessage());}});}
            finally {ui(()->{busy=false;refreshButtons();});}
        });
    }
    private void connect() {
        connectionGeneration++; final long generation=connectionGeneration;
        if (network!=null) network.dispose(); network=null;cloud=null;cloudDevice=null;connected=false;busy=false;
        if(selected==null)return;
        JSONObject device=selected;
        if(device.optString("mode").equals("ir")||device.optString("mode").equals("ac")) {
            if(device.optString("mode").equals("ac")&&!AcEngine.isAvailable()){updateStatus("AC encoder is unavailable on this Android architecture.",false);return;}
            updateStatus(infrared!=null&&infrared.hasEmitter()?"IR ready · hardware response unverified":"No IR transmitter detected on this phone",infrared!=null&&infrared.hasEmitter());return;
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
        card("Your devices",devices.isEmpty()?"Add a TV, decoder or air conditioner. Your device list stays on this phone.":"Saved on this phone. Tap a device to open its remote.");
        for(JSONObject device:devices) {
            LinearLayout c=card(device.optString("name"),device.optString("brand")+" · "+connectionLabel(device));
            LinearLayout actions=row();weighted(actions,action("Open",()->select(device),true),48);
            weighted(actions,action("Remove",()->new AlertDialog.Builder(this).setTitle("Remove remote?").setMessage(device.optString("name"))
                .setNegativeButton("Cancel",null).setPositiveButton("Remove",(dialog,which)->{
                    devices.remove(device);store.delete(device.optString("id"));store.save(devices);
                    if(selected==device){selected=devices.isEmpty()?null:devices.get(0);connect();}render();}).show(),false),48);c.addView(actions);
        }
        full(content,action("+ Add a remote",this::addBrand,true),54);gap(content,12);
        full(content,action("Import learned IR profile",this::importIr,false),54);
    }
    private void helpPage() {
        card("More devices. No Palm account.","Samsung · LG · Panasonic · Hisense · PRISM+ · Daewoo · Astro / NJOI, plus a broad air-conditioner IR catalogue.\nNo subscriptions, advertisements, analytics or app registration.");
        card("Samsung", "Use the published SmartThings API with an existing Samsung account and a token permitted to read devices and execute commands. Only capabilities exposed by your TV are enabled. Power-on depends on TV network settings.");
        card("LG webOS", "Connect the phone and TV to the same trusted Wi-Fi. Enter the TV’s local IP and approve the pairing prompt. This build uses LG’s published Connect SDK protocol on port 3000. Secure-only models are not supported by this transport.");
        card("Panasonic & Hisense", "This build includes selected sourced infrared handset profiles. There is no verified public manufacturer API covering every model. VIDAA, Google TV and Roku are different platforms; brand alone does not establish compatibility.");
        card("PRISM+ & Daewoo TVs", "Select a matching sourced infrared remote profile. These routes require an Android phone with an IR emitter. TV pairing, Bluetooth voice commands and every model are not covered by these IR profiles.");
        card("Air conditioners", "Choose an encoded protocol family for adjustable power, temperature, mode, fan and swing, or a recorded handset profile for captured commands. Settings are requested states, not readings from the unit. A brand match alone does not guarantee model compatibility; unknown brands can import a learned IR profile.");
        card("AC state and synchronization", "Only the last successfully transmitted requested state is stored. The app cannot read the AC state. If someone uses the original remote, reset the last-sent state in this app. Some protocols use toggles or normalize temperature, mode, fan or swing options; test the exact model.");
        card("Astro Ultra Box / Ulti Box", "The supplied decoder profiles are built in; no phone JSON import is needed. Start with the default profile and try the alternate if there is no response. These codes have not been tested on a physical decoder. The supplied Power/Blue and Mute/Right pairs transmit identical signals. An Android phone with an IR transmitter is required.");
        card("Infrared hardware", infrared!=null&&infrared.hasEmitter()?"An infrared transmitter was detected. Aim it at the device and use the exact or compatible handset profile.":"No infrared transmitter detected. IR buttons will stay disabled. Most Android phones and all iPhones lack a built-in consumer IR transmitter. This app does not support USB or external IR hubs.");
        card("Your data stays yours", "Device names and imported profiles stay in private app storage. Pairing keys and SmartThings tokens use Android Keystore encryption. Samsung commands go to api.smartthings.com; LG commands go directly to your TV. Android backup is disabled. Remove devices or clear app data to delete saved credentials.");
        card("Test status", "Automated build and protocol tests do not establish physical device compatibility. No TV, decoder or physical phone was attached during development. This APK is Android-only; iOS requires separate development and signing.");
        full(content,action("Import learned IR profile",this::importIr,false),54);
    }
    private void addBrand() {
        new AlertDialog.Builder(this).setTitle("Add a device").setItems(new String[]{"Television / Astro decoder","Air conditioner · infrared"},
            (dialog,index)->{if(index==1)addAirConditioner();else tvBrands();}).setNegativeButton("Cancel",null).show();
    }
    private void tvBrands() {
        String[] brands={"Samsung","LG","Panasonic","Hisense","PRISM+","Daewoo","Astro"};
        new AlertDialog.Builder(this).setTitle("TV / decoder brand").setItems(brands,(dialog,index)->chooseMethod(brands[index])).setNegativeButton("Cancel",null).show();
    }
    private static final class Choice {
        final String label;final Runnable action;
        Choice(String label,Runnable action){this.label=label;this.action=action;}
        @Override public String toString(){return label;}
    }
    private void choices(String title,List<Choice> options) {
        LinearLayout body=column();body.setPadding(dp(18),0,dp(18),0);
        EditText search=input("Search brand or model",false);body.addView(search);
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
    private void addAirConditioner() {
        if(infrared==null){message("The IR catalogue is loading. Try again in a moment.");return;}
        Set<String> brands=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for(AcEngine.Profile profile:AcEngine.profiles())brands.add(profile.brand);
        brands.addAll(infrared.brands("air_conditioner"));
        List<Choice> options=new ArrayList<>();
        for(String brand:brands)options.add(new Choice(brand,()->acProfiles(brand)));
        options.add(new Choice("Other brand · import a learned AC profile",this::importIr));
        choices("Air-conditioner manufacturer",options);
    }
    private void acProfiles(String brand) {
        List<Choice> options=new ArrayList<>();
        for(AcEngine.Profile profile:AcEngine.profiles())if(profile.brand.equalsIgnoreCase(brand))
            options.add(new Choice("Adjustable · "+profile.name,()->saveAc(profile)));
        for(JSONObject profile:infrared.forBrand(brand,"air_conditioner"))
            options.add(new Choice("Recorded · "+profile.optString("model"),()->saveIr(brand,profile)));
        options.add(new Choice("Import learned "+brand+" AC profile",this::importIr));
        choices(brand+" AC remote / protocol",options);
    }
    private void saveAc(AcEngine.Profile profile) {
        if(!AcEngine.isAvailable()){message("The stateful encoder is unavailable on this architecture. Choose a recorded profile.");return;}
        try {
            JSONObject device=baseDevice(profile.brand,"ac",profile.brand+" · "+profile.name);
            device.put("category","air_conditioner");device.put("profile",profile.id);
            device.put("acDraft",stateJson(new AcEngine.State(true,24,AcEngine.MODE_COOL,AcEngine.FAN_AUTO,false)));
            saveDevice(device);
        }catch(Exception e){message(e.getMessage());}
    }
    private AcEngine.State acState(JSONObject json) {
        if(json==null)return new AcEngine.State(true,24,AcEngine.MODE_COOL,AcEngine.FAN_AUTO,false);
        return new AcEngine.State(json.optBoolean("power",true),json.optInt("temperature",24),json.optInt("mode",AcEngine.MODE_COOL),json.optInt("fan",AcEngine.FAN_AUTO),json.optBoolean("swing",false));
    }
    private JSONObject stateJson(AcEngine.State state) throws Exception {
        return new JSONObject().put("power",state.power).put("temperature",state.temperatureC).put("mode",state.mode).put("fan",state.fan).put("swing",state.swing);
    }
    private void changeAc(AcEngine.State state) {
        try{selected.put("acDraft",stateJson(state));store.save(devices);render();}
        catch(Exception error){message(error.getMessage());}
    }
    private void deviceHeading() {
        LinearLayout device=card(selected.optString("name"),selected.optString("brand")+" · "+connectionLabel(selected));
        statusText=text(status,13,connected?MINT:MUTED,false);statusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        gap(device,8);device.addView(statusText);gap(device,10);full(device,action("Switch device  ▾",this::pickDevice,false),44);
    }
    private void acRemotePage() {
        deviceHeading();
        AcEngine.Profile profile=AcEngine.find(selected.optString("profile"));
        if(profile==null){card("Missing AC profile","Choose an AC protocol again from Add a device.");return;}
        AcEngine.State state;
        try{state=acState(selected.optJSONObject("acDraft"));}catch(Exception e){state=acState(null);}
        final AcEngine.State s=state;
        card("Requested AC settings",profile.notes+"\nAdjust settings, then press Send. The app cannot read the appliance state.");
        LinearLayout temperature=card("TEMPERATURE","");
        TextView value=text(s.temperatureC+"°C",54,MINT,true);value.setGravity(Gravity.CENTER);temperature.addView(value);
        LinearLayout adjust=row();
        weighted(adjust,action("−",()->changeAc(new AcEngine.State(s.power,Math.max(16,s.temperatureC-1),s.mode,s.fan,s.swing)),false),54);
        weighted(adjust,action("+",()->changeAc(new AcEngine.State(s.power,Math.min(32,s.temperatureC+1),s.mode,s.fan,s.swing)),false),54);temperature.addView(adjust);
        full(content,action("Power setting: "+(s.power?"ON":"OFF"),()->changeAc(new AcEngine.State(!s.power,s.temperatureC,s.mode,s.fan,s.swing)),false),52);gap(content,10);
        String[] modes={"Auto","Cool","Heat","Dry","Fan only"};
        full(content,action("Mode: "+modes[s.mode],()->new AlertDialog.Builder(this).setTitle("Requested mode").setItems(modes,
            (d,index)->changeAc(new AcEngine.State(s.power,s.temperatureC,index,s.fan,s.swing))).show(),false),52);gap(content,10);
        String[] fans={"Auto","Low","Medium","High"};int[] fanValues={AcEngine.FAN_AUTO,AcEngine.FAN_LOW,AcEngine.FAN_MEDIUM,AcEngine.FAN_HIGH};
        String fanName=s.fan==0?"Auto":s.fan<=2?"Low":s.fan==3?"Medium":"High";
        full(content,action("Fan: "+fanName,()->new AlertDialog.Builder(this).setTitle("Requested fan speed").setItems(fans,
            (d,index)->changeAc(new AcEngine.State(s.power,s.temperatureC,s.mode,fanValues[index],s.swing))).show(),false),52);gap(content,10);
        full(content,action("Vertical swing: "+(s.swing?"AUTO":"OFF"),()->changeAc(new AcEngine.State(s.power,s.temperatureC,s.mode,s.fan,!s.swing)),false),52);gap(content,16);
        Button send=action("Send AC settings",this::sendAc,true);commandButtons.put(send,"__ac_send");full(content,send,58);gap(content,14);
        content.addView(text("Point the phone’s IR emitter at the AC. Unsupported modes, fan/swing options and temperatures may be normalized by this protocol. Requested settings and last-sent settings do not confirm the AC’s actual state.",13,MUTED,false));gap(content,14);
        full(content,action("Reset last-sent state",()->{
            selected.remove("acLastSent");store.save(devices);message("Last-sent state cleared. First-send toggle behavior depends on the actual AC state.");
        },false),48);refreshButtons();
    }
    private void capturedAcPage() {
        deviceHeading();
        JSONObject profile=infrared==null?null:infrared.find(selected.optString("profile"));
        if(profile==null){card("Profile unavailable","Import the saved handset profile again.");return;}
        card("Recorded AC commands","Each button replays its captured signal and may change several settings at once. Labels describe the source capture; this profile does not generate arbitrary temperatures or modes.");
        JSONObject commands=profile.optJSONObject("commands");if(commands==null)return;
        Iterator<String> keys=commands.keys();
        while(keys.hasNext()){
            String key=keys.next();JSONObject signal=commands.optJSONObject(key);
            String label=signal==null?labelFor(key):signal.optString("label",labelFor(key));
            full(content,key(label.length()>120?label.substring(0,120):label,key,false),54);gap(content,8);
        }
        gap(content,10);content.addView(text("An IR-equipped Android phone and a matching appliance model are required. Transmission does not confirm a response. Physical compatibility is untested.",13,MUTED,false));refreshButtons();
    }
    private void sendAc() {
        if(selected==null||busy||!connected){message("An available IR emitter and AC protocol are required.");return;}
        final JSONObject device=selected;final long generation=connectionGeneration;
        final AcEngine.Profile profile=AcEngine.find(device.optString("profile"));
        final AcEngine.State desired,previous;
        try{desired=acState(device.optJSONObject("acDraft"));previous=device.has("acLastSent")?acState(device.optJSONObject("acLastSent")):null;}
        catch(Exception error){message("Invalid saved AC settings. Add the device again.");return;}
        busy=true;refreshButtons();
        worker.execute(()->{
            try{
                AcEngine.Waveform waveform=AcEngine.encode(profile,desired,previous);
                infrared.transmitSignal(waveform.carrierHz,waveform.patternUs);
                ui(()->{
                    if(devices.contains(device))try{device.put("acLastSent",stateJson(desired));store.save(devices);}catch(Exception e){message("IR sent, but last-sent state could not be saved.");}
                    if(generation==connectionGeneration)updateStatus("AC IR sent · response unverified",true);
                });
            }catch(Exception error){ui(()->{if(generation==connectionGeneration){updateStatus(error.getMessage(),connected);message(error.getMessage());}});}
            finally{ui(()->{busy=false;refreshButtons();});}
        });
    }
    private void chooseMethod(String brand) {
        List<String> options=new ArrayList<>();
        if(brand.equals("Samsung"))options.add("SmartThings · published Samsung API");
        if(brand.equals("LG"))options.add("webOS · local Wi-Fi pairing");
        options.add("Infrared · requires phone IR transmitter");
        new AlertDialog.Builder(this).setTitle(brand+" connection").setItems(options.toArray(new String[0]),(dialog,index)->{
            if(options.get(index).startsWith("SmartThings"))smartThingsSetup();
            else if(options.get(index).startsWith("webOS"))lgSetup();else irSetup(brand);
        }).setNegativeButton("Cancel",null).show();
    }
    private void irSetup(String brand) {
        if(infrared==null){message("IR catalogue is unavailable.");return;}
        List<JSONObject> profiles=infrared.forBrand(brand);
        if(profiles.isEmpty()) {
            new AlertDialog.Builder(this).setTitle(brand+" learned IR profile").setMessage("No verified compatible codes are bundled for this device. Import a learned JSON profile for your exact handset. A phone IR transmitter is required.")
                .setPositiveButton("Import profile",(d,w)->importIr()).setNegativeButton("Cancel",null).show();return;
        }
        String[] labels=new String[profiles.size()+1];
        for(int i=0;i<profiles.size();i++)labels[i]=profiles.get(i).optString("model")+" · hardware untested";
        labels[profiles.size()]="Import another learned profile…";
        new AlertDialog.Builder(this).setTitle(brand+" infrared profile").setItems(labels,(dialog,index)->{
            if(index==profiles.size()){importIr();return;}
            JSONObject profile=profiles.get(index);
            boolean emitter=infrared.hasEmitter();
            String guidance=profile.optString("notice").trim();
            if(!emitter||!guidance.isEmpty()) {
                if(!emitter)guidance+=(guidance.isEmpty()?"":"\n\n")+"You can save this remote, but this phone cannot send infrared commands. Wi-Fi alternatives are available only for supported Samsung and LG models.";
                new AlertDialog.Builder(this).setTitle(emitter?"About this IR profile":"No IR transmitter detected").setMessage(guidance)
                    .setPositiveButton(emitter?"Save remote":"Save anyway",(d,w)->saveIr(brand,profile)).setNegativeButton("Cancel",null).show();
            }else saveIr(brand,profile);
        }).setNegativeButton("Cancel",null).show();
    }
    private void saveIr(String brand,JSONObject profile) {
        try {JSONObject device=baseDevice(brand,"ir",brand+" · "+profile.getString("model"));device.put("profile",profile.getString("id"));device.put("category",profile.optString("category","tv"));saveDevice(device);}
        catch(Exception e){message(e.getMessage());}
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
            finally{ui(()->{busy=false;refreshButtons();});}
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
            finally{ui(()->{busy=false;refreshButtons();});}
        });
    }
    private void importIr() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,80);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==80&&result==RESULT_OK&&data!=null&&data.getData()!=null) {
            if(infrared==null){message("IR catalogue unavailable.");return;}
            try(InputStream stream=getContentResolver().openInputStream(data.getData())) {
                int count=infrared.importProfiles(IrCatalog.read(stream));message("Imported "+count+" learned profile(s). Add a remote to select one.");
            }catch(Exception e){message("Import failed: "+e.getMessage());}
        }
    }
    private void message(String value) {Toast.makeText(this,value==null?"Connection failed":value,Toast.LENGTH_LONG).show();}
    private void ui(Runnable task) {if(!destroyed)runOnUiThread(()->{if(!destroyed)task.run();});}
    @Override protected void onDestroy(){destroyed=true;connectionGeneration++;if(network!=null)network.dispose();worker.shutdownNow();super.onDestroy();}
}
