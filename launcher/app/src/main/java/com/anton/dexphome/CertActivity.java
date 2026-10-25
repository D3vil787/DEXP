package com.anton.dexphome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.security.KeyChain;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Hands the ISRG Root X1 certificate (Let's Encrypt) to the system certificate installer.
 * Android 4.2's root store predates it, so Crosswalk-based apps such as Lampa reject
 * Let's Encrypt sites with "Request was denied for security".
 */
public class CertActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            InputStream in = getAssets().open("isrgrootx1.der");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();

            Intent i = KeyChain.createInstallIntent();
            i.putExtra(KeyChain.EXTRA_CERTIFICATE, buf.toByteArray());
            i.putExtra(KeyChain.EXTRA_NAME, "ISRG Root X1 (Let's Encrypt)");
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "В прошивке нет установщика сертификатов", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось: " + e, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
