package com.universalremote.app.ac;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Device-side native/JNI checks; records waveforms without sending infrared. */
public final class AcEngineInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int checks = 0;
        try {
            int catalogChecks=com.universalremote.app.CatalogDeviceChecks.run(getTargetContext());
            if (!AcEngine.isAvailable()) throw new AssertionError("Native AC library did not load");
            for (AcEngine.Profile profile : AcEngine.profiles()) {
                AcEngine.State before = new AcEngine.State(false, 24, AcEngine.MODE_COOL, AcEngine.FAN_MEDIUM, false);
                AcEngine.State on = new AcEngine.State(true, 24, AcEngine.MODE_COOL, AcEngine.FAN_MEDIUM, false);
                AcEngine.State off = new AcEngine.State(false, 24, AcEngine.MODE_COOL, AcEngine.FAN_MEDIUM, false);
                verify(AcEngine.encode(profile, on, before), profile.id);
                verify(AcEngine.encode(profile, off, on), profile.id);
                verify(AcEngine.encode(profile, on, null), profile.id);
                checks += 3;
            }
            result.putString("stream", "PASS " + checks + " device-side JNI checks across "
                    + AcEngine.profiles().size() + " AC profiles; "+catalogChecks+" Android catalogue checks\n");
            result.putInt("checks", checks);
            result.putInt("catalogChecks",catalogChecks);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL after " + checks + " checks: " + error + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static void verify(AcEngine.Waveform wave, String id) {
        if (wave.carrierHz < 20000 || wave.carrierHz > 60000 || wave.patternUs.length < 3
                || wave.patternUs.length > 2000) throw new AssertionError(id + " invalid waveform");
        long sum = 0;
        for (int value : wave.patternUs) {
            if (value < 1 || value > 1000000) throw new AssertionError(id + " invalid timing");
            sum += value;
        }
        if (sum > 1900000) throw new AssertionError(id + " command exceeds Android duration limit");
    }
}
