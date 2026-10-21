package com.universalremote.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Validates packaged Mi data and Android service detection, without emitting infrared. */
public final class CatalogInstrumentation extends Instrumentation {
    private boolean validateUi;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        validateUi=arguments!=null&&"true".equals(arguments.getString("palm.ui"));
        start();
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            int checks=CatalogDeviceChecks.run(getTargetContext(), message -> {
                Bundle progress=new Bundle();progress.putString("stream",message + "\n");sendStatus(0,progress);
            });
            result.putInt("catalogChecks",checks);
            int uiChecks=validateUi?UiDeviceChecks.run(this,checks,message->{
                Bundle progress=new Bundle();progress.putString("stream",message+"\n");sendStatus(0,progress);
            }):0;
            result.putInt("uiChecks",uiChecks);
            result.putString("stream","PASS "+checks+" Android Mi catalogue/hardware checks"
                +(validateUi?" and "+uiChecks+" emulator UI checks":"")+"\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream","FAIL: "+error+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
