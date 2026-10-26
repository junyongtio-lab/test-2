#!/usr/bin/env python3
"""Create a separate, reproducible limited-catalogue demo project from Palm Remote 1.6.6.

Only selected brand/category assets are copied. The full application's sources,
assets, version and build configuration are never changed by this command.
"""
import argparse,collections,hashlib,json,pathlib,re,shutil
ROOT=pathlib.Path(__file__).resolve().parents[2]
ALLOW={'tv':{'Samsung'},'air_conditioner':{'Panasonic','Midea'},'fan':{'KDK','Panasonic'},'set_top_box':{'Astro'}}

def write_json(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def coverage(profiles):
    categories={}
    for p in profiles:
        c=categories.setdefault(p['category'],dict(profiles=0,commands=0,brands=set()))
        c['profiles']+=1;c['commands']+=p['commandCount'];c['brands'].add(p['brand'])
    for c in categories.values():c['brands']=len(c['brands'])
    return dict(profiles=len(profiles),commands=sum(p['commandCount'] for p in profiles),brands=len({p['brand'] for p in profiles}),categories=categories,requestedBrandsAbsent=[])
def replace(path,old,new):
    s=path.read_text();assert old in s,(str(path),old[:100]);path.write_text(s.replace(old,new))
def create(out):
    out=out.resolve();assert out!=ROOT and not ROOT.is_relative_to(out),'Output must not contain the full project'
    if out.exists():raise SystemExit('Choose a new output directory; existing project will not be overwritten.')
    out.mkdir(parents=True)
    for name in ['build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','LICENSE']:
        shutil.copy2(ROOT/name,out/name)
    shutil.copytree(ROOT/'gradle',out/'gradle');shutil.copytree(ROOT/'app/src/main/java',out/'app/src/main/java');shutil.copytree(ROOT/'app/src/main/res',out/'app/src/main/res')
    shutil.copy2(ROOT/'app/build.gradle',out/'app/build.gradle');shutil.copy2(ROOT/'app/src/main/AndroidManifest.xml',out/'app/src/main/AndroidManifest.xml')
    src=ROOT/'app/src/main/assets';assets=out/'app/src/main/assets';shutil.copytree(src/'licenses',assets/'licenses');(assets/'ir').mkdir(exist_ok=True);shutil.copy2(src/'ir/ATTRIBUTION.md',assets/'ir/ATTRIBUTION.md')
    raw=[];manifest=[]
    def copy_record(p):
        source=src/p['asset'];target=assets/p['asset'];data=source.read_bytes();assert hashlib.sha256(data).hexdigest()==p['assetSha256'];target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        manifest.append(dict(asset=p['asset'],brand=p['brand'],category=p['category'],model=p['model'],sha256=p['assetSha256']))
    archive_ids=set()
    for name in ['ir/mi/index.json','ir/mi_stb/index.json','ir/mi_apk/index.json']:
        index=json.loads((src/name).read_text());index['profiles']=[p for p in index['profiles'] if p['brand'] in ALLOW[p['category']]]
        index['coverage']=coverage(index['profiles']);index['demoScope']=True;write_json(assets/name,index)
        for p in index['profiles']:copy_record(p)
        raw.extend(index['profiles'])
        if name=='ir/mi/index.json':archive_ids={p['id'] for p in index['profiles']}
    order=json.loads((src/'ir/matching_order.json').read_text());order['groups']=[g for g in order['groups'] if g['brand']=='Samsung']
    for g in order['groups']:g['profiles']=[p for p in g['profiles'] if p['id'] in archive_ids]
    order['groups']=[g for g in order['groups'] if g['profiles']];order['demoScope']=True;write_json(assets/'ir/matching_order.json',order)
    pana=json.loads((src/'ir/panasonic_ac/index.json').read_text());assert {p['brand'] for p in pana['profiles']}=={'Panasonic'};write_json(assets/'ir/panasonic_ac/index.json',pana)
    ac=json.loads((src/'ir/mi_ac_database/index.json').read_text());ac['profiles']=[p for p in ac['profiles'] if p['brand'] in ALLOW['air_conditioner']]
    ac['kind']='mi724-demo-ac-source-data';ac['demoScope']=True
    ac['coverage']=dict(profiles=46,recorded=1,stateful=45,controlsImplemented=4,controlsUnavailable=42,brands=2,sourceRecordsOmitted=688)
    assert len(ac['profiles'])==46;write_json(assets/'ir/mi_ac_database/index.json',ac)
    for p in ac['profiles']:copy_record(p)
    java=out/'app/src/main/java/com/universalremote/app'
    p=java/'AcSourceDatabase.java'
    replace(p,'mi724-complete-ac-source-data','mi724-demo-ac-source-data')
    replace(p,'records.size()!=734||recorded!=33||stateful!=701||implemented!=36||brands().size()!=45','records.size()!=46||recorded!=1||stateful!=45||implemented!=4||brands().size()!=2')
    for old,new in [('"profiles")!=734','"profiles")!=46'),('"recorded")!=33','"recorded")!=1'),('"stateful")!=701','"stateful")!=45'),('"controlsImplemented")!=36','"controlsImplemented")!=4'),('"controlsUnavailable")!=698','"controlsUnavailable")!=42'),('"brands")!=45','"brands")!=2'),('"sourceRecordsOmitted")!=0','"sourceRecordsOmitted")!=688')]:replace(p,old,new)
    replace(p,'String kind=p.getString("definitionKind"),control=', 'if(!DemoScope.allowed("air_conditioner",p.getString("brand")))throw new IllegalArgumentException("Brand excluded from demo.");\n            String kind=p.getString("definitionKind"),control=')
    p=java/'ExpandedAcEncoder.java';s=p.read_text();approved=json.loads((ROOT/'tools/ac_expanded/approved.json').read_text());approved=[r for r in approved if r['brand'] in ALLOW['air_conditioner']];assert len(approved)==2
    block=''.join('        approve("'+r['model']+'","'+r['assetSha256']+'");\n' for r in approved)
    s=re.sub(r'(?<=// BEGIN APPROVED SOURCES\n).*?(?=        // END APPROVED SOURCES)',lambda m:block,s,flags=re.S);p.write_text(s)
    (java/'FamilyAcEncoder.java').write_text('''package com.universalremote.app;
import org.json.JSONObject;
import java.util.*;
/** Demo contains only the source-pinned Panasonic/Midea experimental candidates. */
final class FamilyAcEncoder {
    static final String[] MODELS={};
    private final Map<String,JSONObject> profiles=new LinkedHashMap<>();
    FamilyAcEncoder(AcSourceDatabase source) throws Exception {
        for(JSONObject p:ExpandedAcEncoder.profiles(source))profiles.put(p.getString("id").substring(7),p);
    }
    static int family(String model){return -1;}
    static boolean supports(String model){return ExpandedAcEncoder.supports(model);}
    JSONObject find(String id){return profiles.get(id.startsWith("family_")?id.substring(7):id);}
    List<JSONObject> profiles(){return new ArrayList<>(profiles.values());}
    static PanasonicAcEncoder.Signal encode(JSONObject profile,PanasonicAcEncoder.State state,boolean powerPress) throws Exception {
        if(!profile.optBoolean("expanded"))throw new IllegalArgumentException("Candidate excluded from demo.");
        return ExpandedAcEncoder.encode(profile,state);
    }
}
''')
    (java/'DemoScope.java').write_text('''package com.universalremote.app;
/** Crowdfunding demo brand/category allowlist. */
final class DemoScope {
    static boolean allowed(String category,String brand){
        if(category==null||brand==null)return false;
        switch(category){
            case "tv":return brand.equalsIgnoreCase("Samsung");
            case "air_conditioner":return brand.equalsIgnoreCase("Panasonic")||brand.equalsIgnoreCase("Midea");
            case "fan":return brand.equalsIgnoreCase("KDK")||brand.equalsIgnoreCase("Panasonic");
            case "set_top_box":return brand.equalsIgnoreCase("Astro");
            default:return false;
        }
    }
}
''')
    p=java/'IrCatalog.java';replace(p,'String category = profile.getString("category");','String category = profile.getString("category");\n            if(!DemoScope.allowed(category,profile.getString("brand")))throw new IllegalArgumentException("Brand excluded from demo.");')
    p=java/'MainActivity.java';s=p.read_text()
    s=s.replace('card("One home. Your remotes.","No Palm account. No subscription. Choose an appliance to begin.")','card("Palm Remote Demo","Crowdfunding sample · Samsung TVs; Panasonic and Midea ACs; KDK and Panasonic fans; Astro decoders. Choose an appliance and test a matching remote.")')
    start=s.index('        LinearLayout ac=card("All 734 Mi AC profiles"');end=s.index('        if(!devices.isEmpty())',start)
    s=s[:start]+'''        LinearLayout ac=card("Demo AC controls","Panasonic and Midea candidates are included. Test power and each available setting on your appliance. The app cannot read the AC state.");
        gap(ac,12);full(ac,action("Browse demo AC profiles",()->browseAcSource(null),false),52);
'''+s[end:]
    start=s.index('    private void helpPage()');end=s.index('    private void addBrand()',start)
    s=s[:start]+'''    private void helpPage() {
        card("Palm Remote Demo","Crowdfunding sample based on Palm Remote 1.6.6. Included brands: Samsung TVs; Panasonic and Midea air conditioners; KDK and Panasonic fans; Astro decoders. Other brands and their remote assets are excluded from this APK.");
        card("Find a working remote","Choose a category and brand, then Find a working remote · Guided testing. Aim your IR-equipped Android phone at the appliance. Confirm actual responses before saving a match. A brand match alone does not prove model compatibility.");
        card("AC settings","Panasonic parameter controls and experimental Panasonic/Midea AC112 candidates are included. Offered fan, swing, timer and special options depend on the selected profile. Test each function; the app cannot read appliance state. Timers run on the appliance after a successful IR request.");
        card("Astro setup","Five Astro candidates are included. Confirm multiple button responses before saving. Ultra Box, Ulti Box and NJOI model compatibility requires testing.");
        card("Phone infrared","A detected built-in Android IR transmitter is required for sending. Without one you can browse and save an explicitly untested preview. Hardware detection does not prove the appliance responded.");
        card("Demo data","This is a separate app with its own local saved remotes. No account, payment or crowdfunding pledge is collected in the app. The demo uses offline IR controls.");
        card("Test status","Automated catalogue, encoder and build checks do not establish physical appliance compatibility. Confirm the intended effect on your own appliance.");
    }
'''+s[end:]
    s=s.replace('if(category.equals("tv")){brands.add("Samsung");brands.add("LG");}', 'if(category.equals("tv"))brands.add("Samsung");\n        Iterator<String> demoBrands=brands.iterator();while(demoBrands.hasNext())if(!DemoScope.allowed(category,demoBrands.next()))demoBrands.remove();')
    s=s.replace('    private void chooseMethod(String brand,String category) {','    private void chooseMethod(String brand,String category) {\n        if(!DemoScope.allowed(category,brand)){message("This brand is not included in Palm Remote Demo.");return;}')
    s='\n'.join(line for line in s.split('\n') if 'options.add(new Choice("SmartThings ·' not in line and 'options.add(new Choice("webOS ·' not in line)
    s=s.replace('"IR or supported Wi-Fi"','"Samsung infrared remotes"').replace('Choose any decoder brand','Choose Astro')
    s=s.replace('Five Astro decoder remotes are bundled for testing. Samsung SmartThings and supported LG webOS Wi-Fi pairing are also available. PRISM+ is absent from this snapshot.','Five Astro decoder remotes are bundled for testing. This demo offers offline infrared controls only.')
    s=s.replace('All 734 Mi APK AC profiles','All 46 demo AC source profiles')
    s=s.replace('if(sending[0]||!responses.awaitingResponse())return;','if(sending[0]||!responses.awaitingResponse()){return;}')
    a=s.index('    private void lgSetup()');b=s.index('    private void keypad()',a);s=s[:a]+s[b:]
    p.write_text(s)
    p=java/'ExpandedAcEncoder.java';s=p.read_text().replace('AC112_MITSUBISHI','AC112_TIMING_A').replace('AC112_TCL','AC112_TIMING_B');p.write_text(s)
    replace(out/'app/build.gradle',"applicationId 'com.universalremote.app'","applicationId 'com.universalremote.app.demo'")
    replace(out/'app/build.gradle',"versionName '1.6.6'","versionName '1.6.6-demo'")
    p=out/'app/src/main/AndroidManifest.xml';s=p.read_text().replace('android:label="Palm Remote"','android:label="Palm Remote Demo"')
    s='\n'.join(line for line in s.split('\n') if not any(name in line for name in ['android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','android.permission.ACCESS_WIFI_STATE','android.permission.CHANGE_WIFI_MULTICAST_STATE']))
    p.write_text(s)
    tests=out/'app/src/test/java/com/universalremote/app';tests.mkdir(parents=True);shutil.copy2(ROOT/'tools/demo/DemoCatalogueTest.java',tests/'DemoCatalogueTest.java')
    resources=out/'app/src/test/resources';resources.mkdir(parents=True)
    models={r['model'] for r in approved}
    (resources/'ac112-controls.csv').write_text(''.join(line+'\n' for line in (ROOT/'tools/ac_expanded/control-vectors.csv').read_text().splitlines() if line.split(',')[0] in models))
    report=dict(name='Palm Remote Demo',fileName='palmremote demo.apk',versionName='1.6.6-demo',versionCode=15,applicationId='com.universalremote.app.demo',basedOn='Palm Remote 1.6.6',allowedBrands={k:sorted(v) for k,v in ALLOW.items()},rawCoverage=coverage(raw),acSourceRecords=46,panasonicParameterCandidates=6,ac112Definitions=2,ac112TimingCandidates=4,otherBrandRemoteAssetsIncluded=False,networkConnectionsOffered=False,physicalHardwareTested=False,assets=manifest)
    write_json(out/'demo-manifest.json',report)
    print(json.dumps({k:v for k,v in report.items() if k!='assets'},indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=pathlib.Path,required=True);args=parser.parse_args();create(args.output)
