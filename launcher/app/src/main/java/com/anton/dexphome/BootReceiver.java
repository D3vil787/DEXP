package com.anton.dexphome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the auto-HDMI helper after the TV boots. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!AutoHdmiService.isEnabled(c)) return;
        c.startService(new Intent(c, AutoHdmiService.class).setAction(AutoHdmiService.ACTION_POWER_ON));
    }
}
