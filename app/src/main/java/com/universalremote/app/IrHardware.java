package com.universalremote.app;

import android.content.Context;
import android.hardware.ConsumerIrManager;

/** Phone hardware detection, independent of remote profiles and appliance responses. */
final class IrHardware {
    enum State { CHECKING, READY, ABSENT, UNAVAILABLE }

    interface Probe {
        boolean hasEmitter();
        int[][] carrierRanges();
    }

    static final class Result {
        final boolean hasEmitter;
        final State state;
        final String title, detail, diagnostics;

        private Result(boolean hasEmitter, State state, String title, String detail, String diagnostics) {
            this.hasEmitter=hasEmitter;
            this.state=state;
            this.title=title;
            this.detail=detail;
            this.diagnostics=diagnostics;
        }
    }

    private IrHardware() {}

    static Result checking() {
        return new Result(false,State.CHECKING,"Checking IR hardware…",
            "Checking this phone’s built-in IR transmitter.",
            "The Android consumer infrared hardware check has not finished.");
    }

    static Result inspect(Context context) {
        final ConsumerIrManager manager;
        try {
            if(context==null) return unavailable("Android context is unavailable.");
            manager=(ConsumerIrManager)context.getSystemService(Context.CONSUMER_IR_SERVICE);
        } catch(RuntimeException error) {
            return unavailable("The Android IR service query failed ("+error.getClass().getSimpleName()+").");
        }
        // A missing service means this Android device exposes no consumer IR transmitter.
        if(manager==null) return absent("Android consumer infrared service: not available.");
        return inspect(new Probe() {
            @Override public boolean hasEmitter() { return manager.hasIrEmitter(); }
            @Override public int[][] carrierRanges() {
                ConsumerIrManager.CarrierFrequencyRange[] ranges=manager.getCarrierFrequencies();
                if(ranges==null) return null;
                int[][] values=new int[ranges.length][];
                for(int i=0;i<ranges.length;i++) {
                    if(ranges[i]!=null) values[i]=new int[]{ranges[i].getMinFrequency(),ranges[i].getMaxFrequency()};
                }
                return values;
            }
        });
    }

    static Result inspect(Probe probe) {
        if(probe==null) return unavailable("The hardware probe is unavailable.");
        final boolean emitter;
        try { emitter=probe.hasEmitter(); }
        catch(RuntimeException error) {
            return unavailable("Android emitter query failed ("+error.getClass().getSimpleName()+").");
        }
        if(!emitter) return absent("Android hasIrEmitter(): false.");

        // Carrier capabilities are a separate query: failure cannot undo detected hardware.
        final int[][] ranges;
        try { ranges=probe.carrierRanges(); }
        catch(RuntimeException error) {
            return ready(null,"Android carrier query failed ("+error.getClass().getSimpleName()+").");
        }
        if(ranges==null||ranges.length==0) return ready(null,"Android reported no carrier frequency ranges.");
        boolean supported=false, invalid=false;
        StringBuilder description=new StringBuilder("Android carrier frequency ranges: ");
        int valid=0;
        for(int[] range:ranges) {
            if(range==null||range.length!=2||range[0]<=0||range[1]<range[0]) { invalid=true;continue; }
            if(valid++>0) description.append("; ");
            description.append(range[0]).append('–').append(range[1]).append(" Hz");
            if(range[0]<=38000&&range[1]>=38000) supported=true;
        }
        if(valid==0) return ready(null,"Android reported invalid carrier frequency ranges.");
        description.append('.');
        if(invalid) description.append(" Some reported ranges were invalid.");
        // A valid matching range proves support; incomplete non-matching data cannot prove absence.
        return ready(supported?Boolean.TRUE:invalid?null:Boolean.FALSE,description.toString());
    }

    private static Result absent(String diagnostic) {
        return new Result(false,State.ABSENT,"No IR transmitter detected",
            "This phone cannot send IR through Android. IR buttons are disabled.",
            diagnostic+"\nBuilt-in IR emitter: not detected.\nThis check covers Android consumer IR; USB adapters and external hubs are not supported.");
    }

    private static Result unavailable(String diagnostic) {
        return new Result(false,State.UNAVAILABLE,"IR check unavailable",
            "Android could not report IR hardware. Retry the check.",
            diagnostic+"\nBuilt-in IR emitter: unknown. A failed check does not establish that hardware is absent.");
    }

    private static Result ready(Boolean supports38k,String diagnostic) {
        String carrier=supports38k==null?"carrier support unknown":supports38k?"38 kHz supported":"38 kHz is unsupported";
        String carrierDiagnostic=supports38k==null?"unknown":supports38k?"supported":"unsupported";
        return new Result(true,State.READY,"IR ready",
            "Built-in transmitter detected · "+carrier+".",
            "Android hasIrEmitter(): true.\n"+diagnostic+"\n38 kHz carrier: "+carrierDiagnostic+
            ".\nHardware detection does not verify remote codes or appliance response.\nThis check covers Android consumer IR; USB adapters and external hubs are not supported.");
    }
}
