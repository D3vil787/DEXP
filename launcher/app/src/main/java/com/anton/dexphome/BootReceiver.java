package com.anton.dexphome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives boot, network and MStar power broadcasts. Every one is logged; any of them may be
 * the first sign of life after power-on, so each one checks whether a power-on happened.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction() == null ? "?" : i.getAction();
        Events.log(c, "сигнал " + a.replace("android.intent.action.", "").replace("android.net.conn.", ""));
        if (!AutoHdmiService.isEnabled(c)) return;
        String reason = Events.checkPowerOn(c);
        Intent svc = new Intent(c, AutoHdmiService.class);
        if (reason != null) {
            svc.setAction(AutoHdmiService.ACTION_POWER_ON).putExtra(AutoHdmiService.EXTRA_REASON, reason);
        }
        c.startService(svc); // also simply keeps the helper running
    }
}
