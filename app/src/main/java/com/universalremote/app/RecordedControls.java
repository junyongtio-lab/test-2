package com.universalremote.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Presentation only: selects documented source-key spellings; never creates signals. */
final class RecordedControls {
    private static final Map<String,List<String>> ALIASES=new LinkedHashMap<>();
    static {
        alias("power","power","Power","POWER");
        alias("input","input","Input","INPUT");
        alias("up","up","Up","UP");alias("down","down","Down","DOWN");
        alias("left","left","Left","LEFT");alias("right","right","Right","RIGHT");
        alias("ok","ok","OK","enter","Enter","ENTER");
        alias("back","back","Back","BACK");alias("home","home","Home","HOME");
        alias("menu","menu","Menu","MENU");alias("guide","guide","Guide","GUIDE");
        alias("info","info","Info","INFO");alias("mute","mute","Mute","MUTE");
        alias("volume_up","vol+","volume_up");alias("volume_down","vol-","volume_down");
        alias("channel_up","ch+","channel_up");alias("channel_down","ch-","channel_down");
        alias("temperature_up","temperature_up");alias("temperature_down","temperature_down");
        alias("mode","mode","Mode");alias("fan_speed","fanspeed","wind_speed");
        alias("timer","timer","Timer");
        for(int n=0;n<=9;n++)alias("digit_"+n,Integer.toString(n));
    }
    private static void alias(String role,String... values){ALIASES.put(role,Arrays.asList(values));}
    final Map<String,String> roles;
    final List<String> remaining;
    RecordedControls(Iterable<String> sourceKeys) {
        Set<String> keys=new LinkedHashSet<>();for(String key:sourceKeys)keys.add(key);
        Map<String,String> selected=new LinkedHashMap<>();
        for(Map.Entry<String,List<String>> role:ALIASES.entrySet()) {
            for(String alias:role.getValue())if(keys.contains(alias)){
                selected.put(role.getKey(),alias);keys.remove(alias);break;
            }
        }
        roles=Collections.unmodifiableMap(selected);
        List<String> rest=new ArrayList<>(keys);Collections.sort(rest);
        remaining=Collections.unmodifiableList(rest);
    }
    String key(String role){return roles.get(role);}
    List<String> digits(){List<String> result=new ArrayList<>();for(int n=0;n<=9;n++){String key=key("digit_"+n);if(key!=null)result.add(key);}return result;}
}
