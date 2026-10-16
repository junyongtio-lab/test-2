package com.universalremote.app.ac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Model-specific catalogue for the IRremoteESP8266 common A/C API.
 *
 * <p>Source: crankyoldgit/IRremoteESP8266, commit
 * 1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f, IRac::isProtocolSupported(),
 * IRsend.h model enums, and the Supports comments in src/ir_*.h (plus
 * ir_Teknopoint.cpp). The 65 implemented sending families are included.
 * These are community-documented protocol/model mappings, not manufacturer
 * certifications. A brand entry never implies support for every model.
 *
 * <p>KELON168 (including the documented Hisense unit) and other protocols which
 * lack common-API sending support are deliberately excluded. YORK is also
 * excluded: upstream advertises it but its common sender and power operation
 * are unfinished. No Prism+ A/C mapping is documented in the pinned source.
 * Unsupported modes, fan/swing settings and temperature ranges are clamped or
 * ignored by each protocol implementation.
 */
final class AcProfiles {
    static final List<AcEngine.Profile> ALL = Collections.unmodifiableList(build());

    private AcProfiles() { }

    static List<AcEngine.Profile> build() {
        List<AcEngine.Profile> profiles = new ArrayList<>();
        add(profiles, "airton-rd1a1", "Airton", "RD1A1 / SMVH09B-2A2A3NH", "AIRTON", -1,
                "Documented remote RD1A1, A/C reference 409730.");
        add(profiles, "airwell-rc08w", "Airwell", "RC08W / RC04", "AIRWELL", -1,
                "For documented DLS 21 DCI R410 AW and matching RC08W/RC04 remotes.");
        add(profiles, "amcor-tac495", "Amcor", "TAC-495 / TAC-444", "AMCOR", -1,
                "Documented with ADR-853H A/C; match the original remote.");
        add(profiles, "argo-wrem2", "Argo", "WREM2 / Ulisse 13 DCI", "ARGO", 1,
                "WREM2 model. WREM3 uses a different command format.");
        add(profiles, "argo-wrem3", "Argo", "WREM3 / Ulisse Eco", "ARGO", 2,
                "Ulisse Eco Mobile Split (Wi-Fi) touch-button WREM3 remote.");
        add(profiles, "bosch-rg10a", "Bosch", "RG10A(G2S)BGEF / CL3000i", "BOSCH144", -1,
                "Documented CL3000i-Set 26 E A/C.");
        add(profiles, "durastar-rg10r", "Durastar", "RG10R(M2S)/BGEFU1", "BOSCH144", -1,
                "Documented Bosch144-compatible remote; other Durastar remotes may differ.");
        add(profiles, "carrier-ac64", "Carrier", "Carrier 64-bit family", "CARRIER_AC64", -1,
                "64-bit protocol family. Verify against the original remote; Carrier also uses other protocols.");
        add(profiles, "coolix-family", "Coolix", "Coolix-compatible 24-bit family", "COOLIX", -1,
                "Protocol family used by the separately listed matching-brand remotes. Timers use a different protocol.");
        add(profiles, "beko-rg57k7", "Beko", "RG57K7(B)/BGEF", "COOLIX", -1,
                "Documented BINR 070/071 split-type A/C.");
        add(profiles, "midea-rg52d", "Midea", "RG52D/BGE (Coolix)", "COOLIX", -1,
                "Documented MS12FU-10HRDN1-QRD0GW(B) and MSABAU-07HRFN1-QRD0GW.");
        add(profiles, "comfee-breezy", "Comfee", "Breezy Cool Pro 2.6", "COOLIX", -1,
                "Documented Coolix variant; MPD1-12CRN7 instead uses the Midea profile.");
        add(profiles, "tokio-rg51", "Tokio", "RG51/50/BGE", "COOLIX", -1,
                "Documented AATOEMF17-12CHR1SW split-type A/C.");
        add(profiles, "airwell-rc08b", "Airwell", "RC08B (Coolix)", "COOLIX", -1,
                "RC08B uses Coolix; RC08W/RC04 use the separate Airwell profile.");
        add(profiles, "kastron-rg57a7", "Kastron", "RG57A7/BGEF", "COOLIX", -1,
                "Documented inverter remote.");
        add(profiles, "kaysun-coolix", "Kaysun", "Casual CF (Coolix)", "COOLIX", -1,
                "Upstream documents Casual CF under both Coolix and Midea. Match the original remote/protocol.");
        add(profiles, "toshiba-whe1ye", "Toshiba", "WH-E1YE (Coolix)", "COOLIX", -1,
                "Documented RAS-M10YKV-E, RAS-M13YKV-E and RAS-4M27YAV-E.");
        add(profiles, "bosch-rg36b4", "Bosch", "RG36B4/BGE (Coolix)", "COOLIX", -1,
                "Documented B1ZAI2441W/B1ZAO2441W; RG10A uses the Bosch144 profile.");
        add(profiles, "fisher-r51l1", "Fisher", "R51L1/BGE", "COOLIX", -1,
                "Documented Coolix-compatible remote.");
        add(profiles, "corona-ar01", "Corona", "AR-01 / CSH-N series", "CORONA_AC", -1,
                "Documented CSH-N2211, CSH-N2511, CSH-N2811 and CSH-N4011.");

        add(profiles, "daikin-arc433", "Daikin", "ARC433 / ARC466A12 / ARC466A33", "DAIKIN", -1,
                "Documented ARC433 family, M Series, FTXM-M and ARC443A5.");
        add(profiles, "daikin-brc52b63", "Daikin", "BRC52B63 / 17 Series", "DAIKIN128", -1,
                "Documented 17 Series FTXB09AXVJU, FTXB12AXVJU and FTXB24AXVJU.");
        add(profiles, "daikin-arc480a5", "Daikin", "ARC480A5", "DAIKIN152", -1,
                "152-bit Daikin remote family.");
        add(profiles, "daikin-arc423a5", "Daikin", "ARC423A5", "DAIKIN160", -1,
                "160-bit Daikin remote family; exact remote matching is required.");
        add(profiles, "daikin-brc4c153", "Daikin", "BRC4C153 / BRC4C151", "DAIKIN176", -1,
                "Documented FFQ35B8V1B A/C.");
        add(profiles, "daikin-arc477a1", "Daikin", "ARC477A1 / FTXZ NV1B", "DAIKIN2", -1,
                "Documented FTXZ25NV1B, FTXZ35NV1B and FTXZ50NV1B.");
        add(profiles, "daikin-arc433b69", "Daikin", "ARC433B69 / ARC484A4", "DAIKIN216", -1,
                "Documented FTQ60TV16U2 A/C.");
        add(profiles, "daikin-arc466a67", "Daikin", "ARC466A67 / ARC466A58", "DAIKIN312", -1,
                "Documented FTXM20R5V1B A/C.");
        add(profiles, "daikin-dgs01", "Daikin", "DGS01 / FFN-C / FCN-F", "DAIKIN64", -1,
                "Documented FFN-C/FCN-F Series and FTWX35AXV1.");
        add(profiles, "delonghi-paca95", "DeLonghi", "PAC A95", "DELONGHI_AC", -1,
                "PAC A95 protocol. PAC EM90/EM82 use the separately listed Electra profile.");
        add(profiles, "ecoclim-hysfr", "EcoClim", "HYSFR-P348 / ZC200DPO", "ECOCLIM", -1,
                "Documented HYSFR-P348 remote and ZC200DPO A/C.");
        add(profiles, "electra-ykrm003e", "Electra", "YKR-M/003E / Classic INV 17", "ELECTRA_AC", -1,
                "Documented Classic INV 17 / AXW12DCS A/C.");
        add(profiles, "aux-ykrt011", "AUX", "YKR-T/011", "ELECTRA_AC", -1,
                "Documented KFR-35GW/BpNFW=3 A/C.");
        add(profiles, "frigidaire-fgpc102ab1", "Frigidaire", "FGPC102AB1", "ELECTRA_AC", -1,
                "Documented Electra-compatible portable A/C model.");
        add(profiles, "subtropic-ykrh102e", "Subtropic", "YKR-H/102E", "ELECTRA_AC", -1,
                "Documented SUB-07HN1_18Y A/C.");
        add(profiles, "centek-ykrp002e", "Centek", "YKR-P/002E", "ELECTRA_AC", -1,
                "Documented SCT-65Q09 A/C.");
        add(profiles, "aeg-chillflex", "AEG", "Chillflex Pro AXP26U338CW", "ELECTRA_AC", -1,
                "Documented Electra-compatible portable A/C model.");
        add(profiles, "electrolux-ykrh531e", "Electrolux", "YKR-H/531E", "ELECTRA_AC", -1,
                "Documented Electra-compatible remote. Other Electrolux models use different profiles.");
        add(profiles, "delonghi-pacem", "DeLonghi", "PAC EM90 / Pinguino PAC EM82", "ELECTRA_AC", -1,
                "Documented Electra-compatible models; PAC A95 uses a different profile.");
        add(profiles, "eurom-polar16ch", "Eurom", "Polar 16CH", "EUROM", -1,
                "Documented Polar 16CH A/C.");

        add(profiles, "fujitsu-arrah2e", "Fujitsu", "AR-RAH2E / AR-RAC1E / AR-RAE1E", "FUJITSU_AC", 1,
                "Also AR-RCE1E, AR-RAH2U and AR-REG1U. Match the remote exactly: wrong Fujitsu model/swing commands can lock a unit until mains power is cycled.");
        add(profiles, "fujitsu-ardb1", "Fujitsu", "AR-DB1 / AR-DL10", "FUJITSU_AC", 2,
                "Documented AST9RSGCW and ASU30C1. AR-DL10 swing is unsupported.");
        add(profiles, "fujitsu-arreb1e", "Fujitsu", "AR-REB1E / AR-RAH1U / AR-REB4E", "FUJITSU_AC", 3,
                "No horizontal control; documented ASYG7LMCA and ASU12RLF. Use this variant for AR-RAH1U.");
        add(profiles, "fujitsu-arjw2", "Fujitsu", "AR-JW2 / AR-DL3", "FUJITSU_AC", 4,
                "AR-DB1-like format with horizontal control. Match original remote.");
        add(profiles, "fujitsu-arry4", "Fujitsu", "AR-RY4", "FUJITSU_AC", 5,
                "Documented ASTB09LBC; includes this model's clean/filter fields.");
        add(profiles, "fujitsu-arrew4e", "Fujitsu", "AR-REW4E / AR-REW1E", "FUJITSU_AC", 6,
                "Different temperature encoding. Documented ASYG09KETA-B, ASTG09K and ASTG18K.");
        add(profiles, "fujitsu-general-arrce1e", "Fujitsu General", "AR-RCE1E", "FUJITSU_AC", 1,
                "Documented ASHG09LLCA and AOHG09LLC. Match original remote; wrong model/swing commands may require a mains power cycle.");
        add(profiles, "fujitsu-general-arjw17", "Fujitsu General", "AR-JW17 / AR-JW19", "FUJITSU_AC", 2,
                "Upstream documents these remotes as the AR-DB1 model.");
        add(profiles, "fujitsu-general-arjw2", "Fujitsu General", "AR-JW2", "FUJITSU_AC", 4,
                "Documented AR-JW2 remote, with horizontal control.");
        add(profiles, "ogeneral-arrcl1e", "OGeneral", "AR-RCL1E", "FUJITSU_AC", 1,
                "Upstream documents the AR-RAH2E model. Match the remote exactly; incorrect model/swing can require a mains power cycle.");
        add(profiles, "goodweather-zhjt03", "Goodweather", "ZH/JT-03", "GOODWEATHER", -1,
                "Documented Goodweather-compatible remote.");
        add(profiles, "gree-yaw1f", "Gree", "YAW1F / YAA1FBF / YB1F2F / YAN1F1", "GREE", 1,
                "Default Gree model; the source documents YAA1FBF, YB1F2F, YAN1F1 and VIR09HP115V1AH/VIR12HP230V1AH. Match the original remote and verify behavior.");
        add(profiles, "gree-ybofb", "Gree", "YBOFB family", "GREE", 2,
                "Model enum YBOFB; YBOFB2 and YAPOF3 are documented in this family.");
        add(profiles, "gree-yx1fsf", "Gree", "YX1F2F / YX1FSF family", "GREE", 3,
                "Documented YX1F2F mapping; this model includes Energy Saver mode.");
        add(profiles, "ultimate-gree", "Ultimate", "Heat Pump / YAW1F family", "GREE", 1,
                "Documented Ultimate heat pump using the YAW1F model.");
        add(profiles, "ekokai-gree", "EKOKAI", "YAW1F family", "GREE", 1,
                "Upstream model enum documents EKOKAI under YAW1F; exact model compatibility varies.");
        add(profiles, "rusclimate-yaw1f", "RusClimate", "YAW1F / EACS/I-09HAR_X/N3", "GREE", 1,
                "Documented YAW1F remote and EACS/I-09HAR_X/N3 A/C.");
        add(profiles, "green-ybofb", "Green", "YBOFB / YBOFB2", "GREE", 2,
                "Green is the brand spelling in upstream; distinct from a general Gree compatibility claim.");
        add(profiles, "amana-yx1ff", "Amana", "YX1FF / PBC093G00CC", "GREE", 1,
                "Documented Gree-compatible model; confirm operation with the original remote.");
        add(profiles, "cooper-hunter-yb1f2", "Cooper & Hunter", "YB1F2 / CH-S09FTXG", "GREE", 1,
                "Documented Gree-family remote; verify exact model behavior.");
        add(profiles, "vailland-yacifb", "Vailland", "YACIFB / VAI5-035WNI", "GREE", 1,
                "Brand spelling and remote mapping follow the upstream Supports documentation.");
        add(profiles, "soleus-air-yx1fsf", "Soleus Air", "Window A/C / YX1FSF", "GREE", 3,
                "Documented model with Energy Saver mode; other Soleus models may use Neoclima.");

        add(profiles, "haier-hsu07", "Haier", "HSU07-HEA03", "HAIER_AC", -1,
                "Original Haier A/C protocol; YR-W02 and V9014557 use different profiles.");
        add(profiles, "haier-160", "Haier", "KFR-26GW/83@UI-Ge", "HAIER_AC160", -1,
                "Documented Haier 160-bit A/C model.");
        add(profiles, "haier-v9014557-a", "Haier", "V9014557 M47 8D — setting A", "HAIER_AC176", 1,
                "Match setting A on the physical remote.");
        add(profiles, "haier-v9014557-b", "Haier", "V9014557 M47 8D — setting B", "HAIER_AC176", 2,
                "Match setting B on the physical remote.");
        add(profiles, "haier-yrw02", "Haier", "YR-W02 / HSU-09HMC203", "HAIER_AC_YRW02", -1,
                "Documented YR-W02 remote; not the original Haier format.");
        add(profiles, "mabe-v12843", "Mabe", "V12843 HJ200223 / MMI18HDBWCA6MI8", "HAIER_AC176", 1,
                "Documented Haier176-compatible unit, using the default model A encoding. The source does not establish every Mabe model's compatibility.");
        add(profiles, "daichi-dh", "Daichi", "D-H series", "HAIER_AC176", 1,
                "Documented Haier176-compatible D-H A/C, using the default model A encoding.");
        add(profiles, "hitachi-ras35tha6", "Hitachi", "RAS-35THA6 family", "HITACHI_AC", -1,
                "Original Hitachi A/C family. Other listed Hitachi remotes use distinct formats.");
        add(profiles, "hitachi-lt0541-a", "Hitachi", "R-LT0541-HTA — setting A", "HITACHI_AC1", 1,
                "Documented Series VI and KAZE-312KSDP. Match remote setting A.");
        add(profiles, "hitachi-lt0541-b", "Hitachi", "R-LT0541-HTA — setting B", "HITACHI_AC1", 2,
                "Documented R-LT0541-HTA remote model B; match physical remote setting B.");
        add(profiles, "hitachi-rar2p2", "Hitachi", "RAR-2P2 / RAK-25NH5", "HITACHI_AC264", -1,
                "Documented Hitachi 264-bit family.");
        add(profiles, "hitachi-rar3u3", "Hitachi", "RAR-3U3 / RAS-70YHA3", "HITACHI_AC296", -1,
                "Documented Hitachi 296-bit family.");
        add(profiles, "hitachi-rf11t1", "Hitachi", "RF11T1 / RF07T4 / RAS-22NK", "HITACHI_AC344", -1,
                "Documented Hitachi 344-bit family.");
        add(profiles, "hitachi-rar8p2", "Hitachi", "RAR-8P2 / RAS-AJ25H", "HITACHI_AC424", -1,
                "Documented Hitachi 424-bit family.");
        add(profiles, "kelon-onoff", "Kelon", "ON/OFF 9000–12000", "KELON", -1,
                "48-bit Kelon family uses power and swing toggles; keep the app's power state synchronized. Not KELON168 / Hisense DG11R2-01.");
        add(profiles, "kelvinator-yalif", "Kelvinator", "YALIF / KSV series", "KELVINATOR", -1,
                "Documented KSV26CRC/HRC, KSV35CRC/HRC, KSV53HRC, KSV62HRC, KSV70CRC/HRC and KSV80HRC; match original remote.");
        add(profiles, "gree-kelvinator", "Gree", "YAPOF3 / YAP0F8 / YAP2F", "KELVINATOR", -1,
                "Documented GSE-50CI and these remotes use the Kelvinator family.");
        add(profiles, "sharp-yb1fa", "Sharp", "YB1FA / A5VEY (Kelvinator)", "KELVINATOR", -1,
                "Documented Kelvinator-compatible Sharp unit; CRMC remotes use separate Sharp profiles.");
        add(profiles, "lg-ge6711ar2853m", "LG", "6711AR2853M family", "LG", 1,
                "Original 28-bit LG model enum GE6711AR2853M; select the exact matching remote.");
        add(profiles, "lg-akb75215403", "LG", "AKB75215403 / AKB74395308", "LG2", 2,
                "Documented LG2 family and S4-W12JA3AA A/C.");
        add(profiles, "lg-akb74955603", "LG", "AKB74955603 / AKB73315611", "LG2", 3,
                "Documented A4UW30GFA2, AMNW09GSJA0 and MS05SQ NW0; model-specific swing fields.");
        add(profiles, "lg-akb73757604", "LG", "AKB73757604", "LG2", 4,
                "Documented AMNW24GTPA1 and A4UW30GFA2; distinct LG2 model.");
        add(profiles, "lg-6711a20083v", "LG", "6711A20083V / TS-H122ERM1", "LG", 5,
                "Original LG format with vertical swing toggle only.");
        add(profiles, "general-electric-6711ar2853m", "General Electric", "6711AR2853M / AG1BH09AW101", "LG", 1,
                "Upstream documents this General Electric unit under the LG protocol.");

        add(profiles, "midea-family", "Midea", "Midea 48-bit family", "MIDEA", -1,
                "Match a documented Midea-family remote; RG52D/BGE uses the separate Coolix profile.");
        add(profiles, "pioneer-system-rg66", "Pioneer System", "RG66B6(B)/BGEFU1", "MIDEA", -1,
                "Documented RYBO12GMFILCAD, RUBO18GMFILCAD, WS012GMFI22HLD, WS018GMFI22HLD and UB018GMFILCFHD.");
        add(profiles, "comfee-mpd1", "Comfee", "MPD1-12CRN7", "MIDEA", -1,
                "Documented Midea-compatible portable A/C; Breezy Cool Pro 2.6 uses Coolix.");
        add(profiles, "kaysun-midea", "Kaysun", "Casual CF (Midea)", "MIDEA", -1,
                "Upstream documents Casual CF under both Midea and Coolix; match the original remote.");
        add(profiles, "keystone-rg57h4", "Keystone", "RG57H4(B)BGEF", "MIDEA", -1,
                "Documented Midea-compatible remote.");
        add(profiles, "mrcool-rg57a6", "MrCool", "RG57A6/BGEFU1", "MIDEA", -1,
                "Documented Midea-compatible remote; other MrCool models may differ.");
        add(profiles, "danby-r09c", "Danby", "R09C/BCGE / DAC080–120BGUWDB", "MIDEA", -1,
                "Documented DAC080BGUWDB, DAC100BGUWDB and DAC120BGUWDB.");
        add(profiles, "trotec-rg57h", "Trotec", "RG57H / PAC 2100 X / PAC 3900 X", "MIDEA", -1,
                "Documented RG57H(B)/BGE and RG57H3(B)/BGCEF-M; PAC 3200 and PAC 3550 Pro use other profiles.");
        add(profiles, "lennox-rg57a6", "Lennox", "RG57A6/BGEFU1 / MWMA / MCFA", "MIDEA", -1,
                "Documented MWMA009S4-3P, MWMA012S4-3P, MCFA, MCFB, MMDA, MMDB, MWMA and MWMB.");
        add(profiles, "mirage-kkg9ac1", "Mirage", "KKG9A-C1 / VLU family", "MIRAGE", 1,
                "Default Mirage model. Match original remote; KKG29A-C1 uses a second model.");
        add(profiles, "mirage-kkg29ac1", "Mirage", "KKG29A-C1 family", "MIRAGE", 2,
                "Second Mirage model enum; check the original remote's KKG29A-C1 identifier.");
        add(profiles, "maxell-kkg9ac1", "Maxell", "KKG9A-C1 / MX-CH18CF", "MIRAGE", 1,
                "Documented Mirage-compatible Maxell unit.");
        add(profiles, "tronitechnik-kkg29ac1", "Tronitechnik", "KKG29A-C1 / Reykir 9000", "MIRAGE", 2,
                "Documented second Mirage model.");
        add(profiles, "mitsubishi-electric-ac", "Mitsubishi Electric", "KM14A / SG153 / RH151 / SG15D", "MITSUBISHI_AC", -1,
                "Documented MS-GK24VA, MLZ-RX5017AS, MSZ-GV2519, MSZ-SF25VE3, MSZ-ZW4017S and MSZ-FHnnVE families.");
        add(profiles, "mitsubishi-electric-kpoa", "Mitsubishi Electric", "KPOA / MSH-A24WV / MUH-A24WV", "MITSUBISHI112", -1,
                "112-bit Mitsubishi Electric format; distinct from Mitsubishi Heavy Industries.");
        add(profiles, "mitsubishi-electric-parfa32ma", "Mitsubishi Electric", "001CP T7WE10714 / PAR-FA32MA", "MITSUBISHI136", -1,
                "Documented PEAD-RP71JAA ducted A/C.");
        add(profiles, "mitsubishi-heavy-88", "Mitsubishi Heavy Industries", "RKX502A001C / SRKxxZJ-S", "MITSUBISHI_HEAVY_88", -1,
                "88-bit Mitsubishi Heavy Industries family; distinct from Mitsubishi Electric.");
        add(profiles, "mitsubishi-heavy-152", "Mitsubishi Heavy Industries", "RLA502A700B / SRKxxZM-S", "MITSUBISHI_HEAVY_152", -1,
                "152-bit family, also documented SRKxxZMXA-S.");
        add(profiles, "neoclima-zhty01", "Neoclima", "ZH/TY-01 / NS-09AHTI", "NEOCLIMA", -1,
                "Documented Neoclima remote and unit.");
        add(profiles, "soleus-air-zcftl05", "Soleus Air", "ZCF/TL-05 / TTWM1-10-01", "NEOCLIMA", -1,
                "Documented Neoclima-compatible Soleus unit; YX1FSF window models use Gree.");

        add(profiles, "panasonic-lke", "Panasonic", "LKE series", "PANASONIC_AC", 1,
                "LKE model from the upstream Panasonic enum. Select the model matching the original unit.");
        add(profiles, "panasonic-nke", "Panasonic", "NKE series", "PANASONIC_AC", 2,
                "NKE model; use the exact series matching the original remote.");
        add(profiles, "panasonic-dke", "Panasonic", "DKE / DKW / PKR series", "PANASONIC_AC", 3,
                "Documented A75C2616-1, A75C3704 and PN1122V remotes; CS-E12QKEW.");
        add(profiles, "panasonic-jke", "Panasonic", "JKE series / A75C3747", "PANASONIC_AC", 4,
                "Documented CS-YW9MKD A/C.");
        add(profiles, "panasonic-ckp", "Panasonic", "CKP series / A75C2311", "PANASONIC_AC", 5,
                "Documented CS-ME10/12/14CKPG. Keep app state synchronized for toggle commands.");
        add(profiles, "panasonic-rkr", "Panasonic", "RKR series / A75C4762", "PANASONIC_AC", 6,
                "Documented CS-Z9RKR and CS-Z24RKR A/C.");
        add(profiles, "panasonic-a75c2295", "Panasonic", "A75C2295 / CS-E9CKP", "PANASONIC_AC32", -1,
                "32-bit Panasonic family; distinct from the CKP series long-frame profile.");
        add(profiles, "rhoss-idrowall", "Rhoss", "Idrowall MPCV 20/30/35/40", "RHOSS", -1,
                "Documented Idrowall family.");
        add(profiles, "samsung-ac", "Samsung", "DB93-14195A / DB96-24901C", "SAMSUNG_AC", -1,
                "Documented AR09FSSDAWKNFA, AR09HSFSBWKN, AR12KSFPEWQNET, AR12HSSDBWKNEU, AR12NXCXAWKXEU and AR12TXEAAWKNEU.");
        add(profiles, "sanyo-rcs2", "Sanyo", "RCS-2HS4E / RCS-2S4E", "SANYO_AC", -1,
                "Documented SAP-K121AHA and SAP-K242AH A/C.");
        add(profiles, "sanyo-ac88", "Sanyo", "Sanyo 88-bit family", "SANYO_AC88", -1,
                "Common API exposes this protocol family without a specific remote model in the header; confirm against the physical remote.");
        add(profiles, "sharp-a907", "Sharp", "CRMC-A907 / CRMC-A950", "SHARP_AC", 1,
                "Documented AY-ZP40KR and AH-AxSAY A/C.");
        add(profiles, "sharp-a705", "Sharp", "CRMC-A705 JBEZ", "SHARP_AC", 2,
                "Sharp A705 model; exact original remote matching required.");
        add(profiles, "sharp-a903", "Sharp", "CRMC-A903 / CRMC-820 / CRMC-A863", "SHARP_AC", 3,
                "Documented AH-PR13-GL, AH-XP10NRY and AH-A12REVP-1.");
        add(profiles, "tcl-tac09chsd", "TCL", "TAC-09CHSD/XA31I (112-bit)", "TCL112AC", 1,
                "TAC09CHSD model; GYKQ-58(XM) uses TCL96AC and is not included in this common-API catalog.");
        add(profiles, "leberg-lbstor07", "Leberg", "LBS-TOR07", "TCL112AC", 1,
                "Documented TAC09CHSD-compatible A/C.");
        add(profiles, "daewoo-gykq52e", "Daewoo", "GYKQ-52E / DSB-F0934ELH-V", "TCL112AC", 1,
                "Documented TCL112-compatible Daewoo A/C and remote. This does not establish support for every Daewoo A/C.");
        add(profiles, "electrolux-eacm", "Electrolux", "EACM CL/N3 series", "TCL112AC", 1,
                "Documented in the TCL A/C source. YKR-H/531E instead uses the Electra profile.");
        add(profiles, "teknopoint-gz055be1-tcl", "Teknopoint", "GZ-055B-E1 (TCL112 timing)", "TCL112AC", 2,
                "GZ055BE1 model. Use the separate Teknopoint-timing profile if that matches the physical remote.");
        add(profiles, "teknopoint-gz055be1", "Teknopoint", "GZ-055B-E1 / GZ01-BEJ0-000", "TEKNOPOINT", 2,
                "Documented Allegro SSA-09H. Teknopoint timings differ from standard TCL112 timings.");
        add(profiles, "technibel-iroplus", "Technibel", "IRO PLUS", "TECHNIBEL_AC", -1,
                "Documented Technibel remote family.");
        add(profiles, "teco-family", "Teco", "Teco protocol family", "TECO", -1,
                "Protocol family; exact Teco model is not identified in upstream Supports comments. Verify against original remote.");
        add(profiles, "alaska-sac9010qc", "Alaska", "SAC9010QC", "TECO", -1,
                "Documented Teco-compatible A/C and remote.");
        add(profiles, "toshiba-generic-a", "Toshiba", "WH-TA04NE / WH-UB03NJ / generic A", "TOSHIBA_AC", 0,
                "Documented RAS-B13N3KV2, Akita EVO II, RAS-B13N3KVP-E, RAS 18SKP-ES, WC-L03SE and other matching remotes.");
        add(profiles, "carrier-toshiba", "Carrier", "42NQV / 38NYV series (Toshiba)", "TOSHIBA_AC", 0,
                "Documented 42NQV060/050/035/025M2 with 38NYV060/050/035/025M2.");
        add(profiles, "transcold-m1fno6", "Transcold", "M1-F-NO-6", "TRANSCOLD", -1,
                "Documented Transcold A/C; maintain app state for toggle functions.");
        add(profiles, "trotec-pac3200", "Trotec", "PAC 3200", "TROTEC", -1,
                "Original Trotec family. PAC 2100 X/3900 X use Midea and PAC 3550 Pro uses Trotec3550.");
        add(profiles, "trotec-pac3550", "Trotec", "PAC 3550 Pro", "TROTEC_3550", -1,
                "Distinct Trotec3550 protocol.");
        add(profiles, "duux-dxma04", "Duux", "Blizzard Smart 10K / DXMA04", "TROTEC", -1,
                "Documented Trotec-compatible model.");
        add(profiles, "truma-aventa", "Truma", "Aventa / 40091-86700", "TRUMA", -1,
                "Documented Aventa A/C and 40091-86700 remote.");
        add(profiles, "vestel-biox", "Vestel", "BIOX CXP-9", "VESTEL_AC", -1,
                "Documented 9K BTU BIOX CXP-9 A/C.");
        add(profiles, "voltas-full", "Voltas", "Full-function protocol model", "VOLTAS", 0,
                "Upstream full-function model enum. Select 122LZF for its model-specific horizontal-swing behavior.");
        add(profiles, "voltas-122lzf", "Voltas", "122LZF 4011252", "VOLTAS", 1,
                "Documented window A/C; horizontal swing is not supported on this model.");
        add(profiles, "whirlpool-dg11j13a", "Whirlpool", "DG11J1-3A / DG11J1-04", "WHIRLPOOL_AC", 1,
                "Model DG11J13A. Protocol uses power toggles; synchronize app power state with the unit.");
        add(profiles, "whirlpool-dg11j191", "Whirlpool", "DG11J1-91", "WHIRLPOOL_AC", 2,
                "Model DG11J191. Upstream documents SPIS409L/412L and SPIW409L/412L/418L; match original remote.");

        Collections.sort(profiles, (left,right) -> {
            int brand=left.brand.compareToIgnoreCase(right.brand);
            return brand!=0?brand:left.name.compareToIgnoreCase(right.name);
        });
        return profiles;
    }

    private static void add(List<AcEngine.Profile> profiles, String id, String brand,
                            String name, String protocol, int model, String notes) {
        profiles.add(new AcEngine.Profile(id, brand, name, protocol, model, notes));
    }
}
