package com.universalremote.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public final class RecordedControlsTest {
    @Test public void groupsOnlyExplicitSourceSpellings() {
        RecordedControls controls=new RecordedControls(Arrays.asList("power","vol+","ch-","temperature_up","up","OK","0"));
        assertEquals("power",controls.key("power"));assertEquals("vol+",controls.key("volume_up"));
        assertEquals("ch-",controls.key("channel_down"));assertEquals("temperature_up",controls.key("temperature_up"));
        assertEquals("OK",controls.key("ok"));assertEquals(Arrays.asList("0"),controls.digits());assertTrue(controls.remaining.isEmpty());
    }
    @Test public void ambiguousAndRepeatKeysRemainAccessibleWithoutGuessing() {
        RecordedControls controls=new RecordedControls(Arrays.asList("power_r","Power Off","power toggle","VOLUME+","left_r","0_r","temp+"));
        assertTrue(controls.roles.isEmpty());assertEquals(7,controls.remaining.size());
    }
    @Test public void duplicateAliasesKeepIndependentSourceAssociations() {
        RecordedControls controls=new RecordedControls(Arrays.asList("ok","OK","enter","power","Power","fanspeed","wind_speed"));
        assertEquals("ok",controls.key("ok"));assertEquals("power",controls.key("power"));assertEquals("fanspeed",controls.key("fan_speed"));
        assertEquals(Arrays.asList("OK","Power","enter","wind_speed"),controls.remaining);
    }
    @Test public void everySourceKeyOccursExactlyOnce() {
        Set<String> source=new HashSet<>(Arrays.asList("1","2","2_r","ok","enter","input","INPUT 1","red","power_r","power","vol+","Ch+"));
        RecordedControls controls=new RecordedControls(source);
        Set<String> all=new HashSet<>(controls.roles.values());all.addAll(controls.remaining);
        assertEquals(source,all);assertEquals(source.size(),controls.roles.size()+controls.remaining.size());
    }
    @Test public void missingButtonsAreNeverInvented() {
        RecordedControls controls=new RecordedControls(Arrays.asList("sleep","fresh"));
        assertNull(controls.key("power"));assertNull(controls.key("up"));assertTrue(controls.digits().isEmpty());
        assertEquals(Arrays.asList("fresh","sleep"),controls.remaining);
    }
}
