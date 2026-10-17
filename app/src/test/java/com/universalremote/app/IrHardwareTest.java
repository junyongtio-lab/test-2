package com.universalremote.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class IrHardwareTest {
    private static final class FakeProbe implements IrHardware.Probe {
        boolean emitter=true;
        int[][] ranges={{30000,40000}};
        RuntimeException emitterFailure,carrierFailure;
        int carrierQueries;
        @Override public boolean hasEmitter() {
            if(emitterFailure!=null) throw emitterFailure;
            return emitter;
        }
        @Override public int[][] carrierRanges() {
            carrierQueries++;
            if(carrierFailure!=null) throw carrierFailure;
            return ranges;
        }
    }

    @Test public void pendingCheckDoesNotClaimHardwareOrAbsence() {
        IrHardware.Result result=IrHardware.checking();
        assertEquals(IrHardware.State.CHECKING,result.state);
        assertEquals("Checking IR hardware…",result.title);
        assertFalse(result.hasEmitter);
    }

    @Test public void detectedEmitterWithMatchingRangeIsReadyWithoutClaimingApplianceResponse() {
        IrHardware.Result result=IrHardware.inspect(new FakeProbe());
        assertEquals(IrHardware.State.READY,result.state);
        assertEquals("IR ready",result.title);
        assertTrue(result.hasEmitter);
        assertTrue(result.detail.contains("38 kHz supported"));
        assertTrue(result.diagnostics.contains("30000–40000 Hz"));
        assertTrue(result.diagnostics.contains("does not verify remote codes or appliance response"));
    }

    @Test public void absentEmitterNeverQueriesCarrierRanges() {
        FakeProbe probe=new FakeProbe();probe.emitter=false;
        probe.carrierFailure=new IllegalStateException("Must not be queried");
        IrHardware.Result result=IrHardware.inspect(probe);
        assertEquals(IrHardware.State.ABSENT,result.state);
        assertEquals("No IR transmitter detected",result.title);
        assertFalse(result.hasEmitter);
        assertEquals(0,probe.carrierQueries);
    }

    @Test public void failedEmitterQueryDoesNotFalselyReportMissingHardware() {
        FakeProbe probe=new FakeProbe();probe.emitterFailure=new SecurityException("Denied");
        IrHardware.Result result=IrHardware.inspect(probe);
        assertEquals(IrHardware.State.UNAVAILABLE,result.state);
        assertEquals("IR check unavailable",result.title);
        assertFalse(result.hasEmitter);
        assertTrue(result.diagnostics.contains("SecurityException"));
        assertEquals(0,probe.carrierQueries);
    }

    @Test public void carrierQueryFailurePreservesConfirmedEmitter() {
        FakeProbe probe=new FakeProbe();probe.carrierFailure=new UnsupportedOperationException();
        IrHardware.Result result=IrHardware.inspect(probe);
        assertEquals(IrHardware.State.READY,result.state);
        assertTrue(result.hasEmitter);
        assertTrue(result.detail.contains("carrier support unknown"));
        assertTrue(result.diagnostics.contains("UnsupportedOperationException"));
        assertTrue(result.diagnostics.contains("38 kHz carrier: unknown"));
    }

    @Test public void omittedAndEmptyCarrierRangesMeanUnknownRatherThanUnsupported() {
        for(int[][] ranges:new int[][][]{null,new int[0][]}) {
            FakeProbe probe=new FakeProbe();probe.ranges=ranges;
            IrHardware.Result result=IrHardware.inspect(probe);
            assertEquals(IrHardware.State.READY,result.state);
            assertTrue(result.hasEmitter);
            assertTrue(result.detail.contains("carrier support unknown"));
        }
    }

    @Test public void bothInclusiveRangeBoundariesSupport38k() {
        for(int[][] ranges:new int[][][]{{{38000,38000}},{{38000,40000}},{{30000,38000}},{{20000,25000},{38000,40000}}}) {
            FakeProbe probe=new FakeProbe();probe.ranges=ranges;
            assertTrue(IrHardware.inspect(probe).detail.contains("38 kHz supported"));
        }
    }

    @Test public void unsupported38kDoesNotHideEmitter() {
        FakeProbe probe=new FakeProbe();probe.ranges=new int[][]{{30000,37999},{38001,60000}};
        IrHardware.Result result=IrHardware.inspect(probe);
        assertEquals(IrHardware.State.READY,result.state);
        assertTrue(result.hasEmitter);
        assertTrue(result.detail.contains("38 kHz is unsupported"));
        assertTrue(result.diagnostics.contains("38 kHz carrier: unsupported"));
    }

    @Test public void malformedRangesCannotProduceFalseUnsupportedResult() {
        for(int[][] ranges:new int[][][]{{null},{{0,60000}},{{40000,30000}},{{38000}},{{30000,37000},null}}) {
            FakeProbe probe=new FakeProbe();probe.ranges=ranges;
            IrHardware.Result result=IrHardware.inspect(probe);
            assertEquals(IrHardware.State.READY,result.state);
            assertTrue(result.detail.contains("carrier support unknown"));
        }
    }

    @Test public void validMatchingRangeRemainsKnownWhenAnotherEntryIsMalformed() {
        FakeProbe probe=new FakeProbe();probe.ranges=new int[][]{null,{38000,38000}};
        assertTrue(IrHardware.inspect(probe).detail.contains("38 kHz supported"));
    }

    @Test public void missingProbeReportsUnavailableRatherThanAbsent() {
        IrHardware.Result result=IrHardware.inspect((IrHardware.Probe)null);
        assertEquals(IrHardware.State.UNAVAILABLE,result.state);
        assertEquals("IR check unavailable",result.title);
    }
}
