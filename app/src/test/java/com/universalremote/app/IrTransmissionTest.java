package com.universalremote.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class IrTransmissionTest {
    private static final int[] PATTERN={9000,4500,560,1690,560};
    private static final class Phone implements IrCatalog.Emitter {
        boolean present=true;
        int[][] ranges={{30000,60000}};
        RuntimeException emitterFailure,rangeFailure,sendFailure;
        int rangeQueries,sends,frequency;
        int[] timings;
        @Override public boolean hasEmitter(){if(emitterFailure!=null)throw emitterFailure;return present;}
        @Override public int[][] carrierRanges(){rangeQueries++;if(rangeFailure!=null)throw rangeFailure;return ranges;}
        @Override public void transmit(int hz,int[] pattern){sends++;if(sendFailure!=null)throw sendFailure;frequency=hz;timings=pattern.clone();}
    }
    private void assertSent(Phone phone,int hz) {
        IrCatalog.transmitSignal(phone,hz,PATTERN);
        assertEquals(1,phone.sends);assertEquals(hz,phone.frequency);assertArrayEquals(PATTERN,phone.timings);
    }
    @Test public void confirmedEmitterCanTransmitWhenOptionalRangeQueryFails() {
        for(RuntimeException failure:new RuntimeException[]{new UnsupportedOperationException(),new SecurityException(),new IllegalStateException()}) {
            Phone phone=new Phone();phone.rangeFailure=failure;assertSent(phone,38000);assertEquals(1,phone.rangeQueries);
        }
    }
    @Test public void omittedOrEmptyRangesAllowUnchangedValidatedSignals() {
        for(int[][] ranges:new int[][][]{null,new int[0][]}){Phone phone=new Phone();phone.ranges=ranges;assertSent(phone,38000);}
    }
    @Test public void matchingRangesIncludeBothBoundariesAndDoNotChangeCarrier() {
        for(int hz:new int[]{30000,38000,60000})assertSent(new Phone(),hz);
    }
    @Test public void knownUnsupportedCarrierNeverReachesTransmitter() {
        Phone phone=new Phone();phone.ranges=new int[][]{{40000,41000},{50000,56000}};
        try{IrCatalog.transmitSignal(phone,38000,PATTERN);fail("Unsupported carrier sent");}
        catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("carrier frequency"));}
        assertEquals(0,phone.sends);
    }
    @Test public void incompleteCapabilitiesDoNotFalselyRejectConfirmedEmitter() {
        for(int[][] ranges:new int[][][]{new int[][]{null},new int[][]{{40000,41000},null},new int[][]{{0,40000}},new int[][]{{40000,30000}},new int[][]{{38000}}}) {
            Phone phone=new Phone();phone.ranges=ranges;assertSent(phone,38000);
        }
    }
    @Test public void absentEmitterNeverQueriesRangesOrSends() {
        Phone phone=new Phone();phone.present=false;phone.rangeFailure=new IllegalStateException("Must not query");
        try{IrCatalog.transmitSignal(phone,38000,PATTERN);fail("Absent emitter sent");}
        catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("no infrared transmitter"));}
        assertEquals(0,phone.rangeQueries);assertEquals(0,phone.sends);
    }
    @Test public void missingAndroidServiceCannotSend() {
        try{IrCatalog.transmitSignal(null,38000,PATTERN);fail("Missing service sent");}
        catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("no infrared transmitter"));}
    }
    @Test public void emitterQueryFailurePropagatesWithoutClaimingAbsence() {
        Phone phone=new Phone();phone.emitterFailure=new SecurityException("Emitter query denied");
        try{IrCatalog.transmitSignal(phone,38000,PATTERN);fail("Emitter query failure swallowed");}
        catch(SecurityException expected){assertSame(phone.emitterFailure,expected);}
        assertEquals(0,phone.rangeQueries);assertEquals(0,phone.sends);
    }
    @Test public void actualTransmitFailurePropagatesAfterUnknownCarrierQuery() {
        Phone phone=new Phone();phone.rangeFailure=new UnsupportedOperationException();phone.sendFailure=new SecurityException("Transmission denied");
        try{IrCatalog.transmitSignal(phone,38000,PATTERN);fail("Transmission failure swallowed");}
        catch(SecurityException expected){assertSame(phone.sendFailure,expected);}
        assertEquals(1,phone.sends);
    }
    @Test public void invalidSignalsNeverQueryCapabilitiesOrReachHardware() {
        Phone phone=new Phone();
        for(int[] invalid:new int[][]{null,{9000,-1,560},{9000,4500},{1000000,1000000,560}}) {
            try{IrCatalog.transmitSignal(phone,38000,invalid);fail("Invalid pattern sent");}
            catch(IllegalArgumentException expected){/* Validation must precede capability lookup and transmit. */}
        }
        try{IrCatalog.transmitSignal(phone,19000,PATTERN);fail("Invalid carrier sent");}
        catch(IllegalArgumentException expected){/* No carrier substitution is permitted. */}
        assertEquals(0,phone.rangeQueries);assertEquals(0,phone.sends);
    }
}
