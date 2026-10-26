package com.universalremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Source-pinned 112-bit protocol candidates. Basic state plus bounded, source-mapped additional controls. */
final class ExpandedAcEncoder {
    private static final Map<String,String> APPROVED=new LinkedHashMap<>();
    static {
        // BEGIN APPROVED SOURCES
        approve("7_2807","0ea8d60ed28deb22f31f12f636254b3ce330688d4238be4f30556238917dfc2f");
        approve("7_3022","64b9b9c44d16f306daf17c759b5d6f0a1b6d3f40f6843fa48edecb7fac521dfd");
        approve("kk_3_2275_2807","8c9e202361fe50bebff8b559688c5cd5e8bf32d17cc6327cc805bc49931b3a1a");
        approve("kk_3_2894_2807","60baff2af4fdf7c7a77af9b6bc9346b00cdd81bc220c8b792a603853244be21e");
        approve("kk_3_2894_3200","fc639ee9751d40a994b91563f9c5655b5dde24a0f382a314f56a35c406d06624");
        approve("579_2807","8c25ac2c0a67db5feb15e0984e47c06dab34268741c7e0f532f6127772ca5cb7");
        approve("169_2807","f92362e7f36fa73d982c1b6b4e680d97b9b8c3fe0eb16c2322a926ea32a96181");
        approve("171_2807","cc2106b521d2871758daf467b31fd216d9850170dc2837fbe682bff177d5e70e");
        approve("171_3022","b352260762522ebc7b5363b7031f12a002ef6079115c6e1250d522bcba72b142");
        approve("590_2807","9eb312801ca75b164725b4bf3fd75b6c394cf0e71bf5e2cc5b0ce3c52ea63527");
        approve("195_2807","c24346c3c2fde7cffada37491bc54eef24d3a6a486e295fedb141ce831f8dfd7");
        approve("195_3022","0866d02cc8aee09e70bcb005504e0a60c295173f843d5cda8e0b447810f40ff0");
        approve("58_2807","e111dc0038c9ef2f22b939e4a646dcf7b5c2542fac1b479b69037c3f16287d0c");
        approve("kk_3_3226_2807","1f932ee6300a2858631c269d92401d809b80f09806b5021dfc14c53357bc725d");
        approve("kk_3_3226_3200","5b9c2caf9166387f2cb6badc7097a67f41faba8c76ac60b7a06c8f2e565084ef");
        approve("1949_2807","988da9639667a703120dfbdedcf73bde2751a5f644d02c8ba396d2b63b3670cf");
        approve("kk_3_1949_3200","da3699fa98a43dbb943dd54ad0b58ada1296468356566812c47cd69382abb4e8");
        approve("563_2807","92e3cc51045055841512aa88d994ac017efa3db37fb010f6037ab4e79584aa29");
        approve("kk_3_563_4971","cd94ec30ee5e2d63a8855986d929f78aea0a668c2590d4911b3381d974345c22");
        approve("70_2807","604f1a6d97b44211657d83ebc3157b3db2ff00a8aec5b3ed4912fc38b8e0604f");
        approve("71_2807","ac1463d3eac777c14c3dd1706d823ff4e16b0d65cdb0ddab7e9eed3acdf772e9");
        approve("71_3022","e7162dce6cd0bbfc4516d4225506ba46e4d64865e1c618348b1370a7a0d1acf0");
        approve("kk_3_71_4370","766819f5409a7f3c65fa0d9cc7306c3adf8fdfcbef71cc94f231afb371bb73a7");
        approve("kk_3_1813_2807","4053b20068dc4415eaa4ee526ed28dea5e5ab80c5612e5212e9e471bc48496cb");
        approve("72_2807","2cd45f156e824bbe84decab96feaf434ef164e1645dba8ff2b10f587d18dab54");
        approve("74_2807","619bf1097d33d0af0df3772fc26290a27e1018dcbc44673dbc8d60cb572f2648");
        approve("75_2807","9fb39dd0948a25e12c2a83969aed44c324eb571485f920cd00d98cd65357e45c");
        approve("117_2807","4fcf05f73c229264f4a34ce5ef22b7b1ed863442ca9db4f68986cf76bb508483");
        approve("kk_3_238_2807","3947c5a5c3a226fb2dd6404ead314a8fbd5f6568f7964da62c08c3ebffa823fa");
        approve("kk_3_238_3022","3f10954dd9a4eec38f9b2b340aaa2506c155d0c81fbf37daf5a15bdc897aa64f");
        approve("92_2807","587194cb0c854800a1398c57d7f948749a8faade56d4d535519f067b6c582f23");
        approve("22_2807","fe42d6aa52e5a3a9f4442daccc6dda3932d3bec52a3975942fcd93a1cb7ad4d0");
        approve("kk_3_2624_2807","80d45239add287a53bbd86eafee1c89eab7e6dc6b1ff33dd4c1364e378c5a829");
        approve("kk_3_2624_3200","08aa2a10d3b382b796de742b4450e217444cb6d8c5ac3a266af70d96e20d751c");
        approve("kk_3_574_2807","a93522f738ef51c222a79d2a28e62db9e10c9ac9f14c26626353e951d4bfcd7c");
        approve("110_2807","7d3ba7843b07600416b77dc65932c4cd0eb1438c8f4b8731bf6103f1eb576eee");
        approve("kk_3_3223_2807","848d684f5937abf0debadd75d63feed46d44383b94ff6b5da99c0c6b38092bad");
        approve("kk_3_3223_3200","0e107468b8269290618c8a0f8acbc271d7063c896cc36e476c574dcdd231d3fa");
        approve("kk_3_123_2807","f79c6fe056e559337c8072db53fd99c58d0090ae7786febb0af7d83b438a5a1d");
        approve("kk_3_123_4971","2dc8e32a4ebc8d10b44fad6b6c12c3c45251fc4573d68372765e5ea9a226e810");
        approve("kk_3_123_5681","0d7b2ffa8d7c64aff1272dabf04cb51db6a56c594b3e62ddc34aaf4e520dffd4");
        approve("kk_3_124_2807","f3f99bc5d51d4d72d836054964e1cdc35918a1532d06c186efab82b1ed4f18a4");
        approve("kk_3_124_3022","1bacd2442018c61e771902ed89c8fac6bcf4ae02d606257ab3ec614d22b21cbf");
        approve("kk_3_124_3266","b93820e0c7592a921175eb10c06f69315df2cd796bd7bceaae886d1b833de99c");
        approve("1876_2807","214fcb2f477da6ec14ab052e93bfa0a0eabaf516b70d39d45733591c340d8d13");
        approve("kk_3_2664_2807","9b4c20c1cf5bb584b81620487ce6abef970970b769ec9f466185c38efc089bdf");
        approve("137_2807","b3dd6e8d9b37dc3278798a431d81efa64e75d3985aaff49b327a73935672da7e");
        approve("125_2807","4ea943b5a59d5a8da07c4ea67a57424a72d3245073f19f27f3005179d96a0c56");
        approve("126_2807","4d9f2a280ab5ec5614492f35557891ceabf27a639463fa728a14887ac5b524fd");
        approve("149_2807","da7a1f168632c77b74004e5f703189d20ec545e320b73a9aeec726040a50b228");
        approve("178_2807","614089497037e9e9d8c10fe10736dc6edf952e7fe3f2a4954ed68cf0b89cc75a");
        approve("178_3022","bba1595011bfe38c4a188351f200dca2d97cb95f36bc76d6ebde2518b84aa73c");
        approve("28_3022","acb0dfe12adb90d2451900c31f242257802ffa2c4dfe564941019ed86a9b001f");
        approve("28_6012","01b7d053c80077e0ed74c390e2150d3cc329ab44f63f94d33b6e5dec88b954da");
        approve("28_6022","e6bd0c97e27dc332c4e191918c47a0a6725eb588164f96d74d6072c4d6483bb1");
        approve("kk_3_28_3200","49a8c2722d9bdca607aad40279709c66f57bf80dad8589e1d8c0ad5b80c41ffd");
        approve("kk_3_28_4971","9cb3c3ce93909e41f0dcf5f45d43a74802a3508253e22efa2207ba95882c0c86");
        approve("kk_3_28_5124","83b300fcc9bef655103bc7debf8b6f4e3156d77db5fd789e6099229e79193954");
        approve("1760_2807","2334acc95e3fd1feaa311fb73a396d80c5fa82907679a38f23ab927122a003ff");
        approve("64_2807","76b43816065dc5290c7529f013a5590a0a9b88d1f728ce9cad06b45135c2bf78");
        approve("1956_2807","e9a1765e4b84c0976e44329c5acf39895c32af5985c6c7527b9939de362cab24");
        approve("kk_3_2567_2807","af3d3d249a8d47ea12d74bcdad30a671a99c9ed22260fe43dc9204373413ae71");
        approve("kk_3_2567_3200","1edfd7ac1e109b5261fde194ba1cc25666313444d8c1a862ccbbc762d6d1540f");
        approve("kk_3_2258_3200","c558b2d50a9a033da48d70d9307dc7468eb253e6d16a400b51406024dd35a6f6");
        approve("kk_3_2276_3200","39e7e73625d594f2ef5f46d8e5e87c7f1f7509e2198540726013918824bd8c8b");
        approve("580_2807","8e7ca805f16a20bbdae619c1f2a27ff8c8ec8b934a6e469428791973b07e3083");
        approve("161_4727","59bb36a5d934123708f4d3097a7918f891f057f3078adecc44a61bcd48eafc42");
        approve("161_6702","b871c3d824edb9723c61cc450877148ca2ebe057c2da9160d8042e3babc48f1d");
        approve("560_2807","dd64f7719714d825d8b1363bb44fc696635196cc36502b5e5cd3e6dc8cbca2a5");
        approve("kk_3_560_3200","d7f735534ced00cc200ff2153a88799c3a578cbf95b7041ccfb27223dbfbac10");
        // END APPROVED SOURCES
    }
    private static void approve(String model,String asset){APPROVED.put(model,asset);}
    static boolean supports(String model){return APPROVED.containsKey(model);}
    static List<JSONObject> profiles(AcSourceDatabase source) throws Exception {
        List<JSONObject> result=new ArrayList<>();
        for(JSONObject metadata:source.profiles(null)) {
            String model=metadata.getString("model");if(!supports(model))continue;
            if(!APPROVED.get(model).equals(metadata.getString("assetSha256")))throw new IllegalArgumentException("AC112 source asset changed.");
            JSONObject definition=source.readRecord(metadata.getString("id")).getJSONObject("definition");
            for(int variant=0;variant<2;variant++) {
                JSONObject p=new JSONObject(metadata.toString());
                p.put("id","family_"+model+(variant==0?"":"_tcl112")).put("family",variant==0?"AC112_MITSUBISHI":"AC112_TCL")
                    .put("expanded",true).put("variant",variant).put("carrierHz",38000).put("sourceCarrierHz",metadata.getInt("carrierHz"))
                    .put("definition",definition).put("definitionDigest",IrCatalog.sha256(definition.toString()))
                    .put("powerToggle",false).put("supportsFan",true).put("supportsSwing",true).put("additionalControls",Ac112Controls.describe(definition))
                    .put("modes",new JSONArray().put("cool").put("heat"));
                result.add(p);
            }
        }
        if(result.size()!=APPROVED.size()*2)throw new IllegalArgumentException("AC112 candidate sources are missing.");
        return result;
    }
    static PanasonicAcEncoder.Signal encode(JSONObject p,PanasonicAcEncoder.State state) throws Exception {
        String model=p.getString("model");
        if(!supports(model)||!APPROVED.get(model).equals(p.getString("assetSha256"))||state==null||state.mode==null
            ||!(state.mode.equals("cool")||state.mode.equals("heat"))||state.temperature<16||state.temperature>30)
            throw new IllegalArgumentException("Unsupported AC112 candidate or requested control.");
        JSONObject d=p.getJSONObject("definition");
        if(!IrCatalog.sha256(d.toString()).equals(p.getString("definitionDigest")))throw new IllegalArgumentException("AC112 definition changed.");
        byte[] source=PanasonicAcEncoder.hex(d.getString("1002"));
        if(source.length!=15||source[0]!=14||source[1]!=0x23||(source[2]&255)!=0xcb||source[3]!=0x26||source[4]!=1)
            throw new IllegalArgumentException("Unsupported AC112 packet.");
        byte[] packet=java.util.Arrays.copyOfRange(source,1,15);
        packet[5]=(byte)((packet[5]&~4)|(state.power?4:0));
        packet[6]=(byte)((packet[6]&0xf0)|(state.mode.equals("heat")?1:3));
        packet[7]=(byte)((packet[7]&0xf0)|(31-state.temperature));
        packet[12]=(byte)(packet[12]&~0x20);
        Ac112Controls.apply(d,state,packet);
        int sum=0;for(int i=0;i<13;i++)sum+=packet[i]&255;packet[13]=(byte)sum;
        int variant=p.getInt("variant");
        String family=p.getString("family");
        if(variant<0||variant>1||!family.equals(variant==0?"AC112_MITSUBISHI":"AC112_TCL")||p.getInt("carrierHz")!=38000)
            throw new IllegalArgumentException("Unsupported AC112 transport.");
        int mark=variant==0?450:500,zero=variant==0?385:325,one=variant==0?1250:1050;
        int[] pattern=new int[228];int at=0;
        pattern[at++]=variant==0?3450:3000;pattern[at++]=variant==0?1696:1650;
        for(byte b:packet)for(int bit=0;bit<8;bit++){pattern[at++]=mark;pattern[at++]=((b>>>bit)&1)==0?zero:one;}
        pattern[at++]=mark;pattern[at]=100000;
        IrCatalog.validateSignal(38000,pattern);
        return new PanasonicAcEncoder.Signal(38000,pattern,packet);
    }
}
