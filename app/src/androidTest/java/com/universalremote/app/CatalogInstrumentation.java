package com.universalremote.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Validates packaged Mi data and Android service detection, without emitting infrared. */
public final class CatalogInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            int checks=CatalogDeviceChecks.run(getTargetContext());
            result.putInt("catalogChecks",checks);
            result.putString("stream","PASS "+checks+" Android Mi catalogue/hardware checks\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream","FAIL: "+error+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
