package projekt.interfacer.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Settings;
import android.os.UserHandle;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import projekt.interfacer.utils.IOUtils;

public class InterfacerService extends Service {
    private static final String TAG = "InterfacerService";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case "projekt.interfacer.INITIALIZE":
                        Log.d(TAG, "Initializing Interfacer...");
                        initializeInterfacer();
                        break;
                    case "projekt.interfacer.APPLY_OVERLAYS":
                        Log.d(TAG, "Applying overlays...");
                        applyOverlays();
                        break;
                    case "projekt.interfacer.APPLY_SOUNDS":
                        Log.d(TAG, "Applying sounds...");
                        applySounds();
                        break;
                    default:
                        Log.d(TAG, "Unknown action: " + action);
                }
            }
        } else {
            Log.d(TAG, "Service started without intent action");
        }
        return START_STICKY;
    }

    private void initializeInterfacer() {
        Log.d(TAG, "Initializing directories and resources...");
        IOUtils.createDirIfNotExists(IOUtils.THEME_CACHE_DIR);
        IOUtils.createDirIfNotExists(IOUtils.FONTS_CACHE_DIR);
        IOUtils.createDirIfNotExists(IOUtils.AUDIO_CACHE_DIR);
        IOUtils.createDirIfNotExists(IOUtils.UI_SOUNDS_DIR);
        IOUtils.createDirIfNotExists(IOUtils.ALARMS_DIR);
        IOUtils.createDirIfNotExists(IOUtils.NOTIFICATIONS_DIR);
        IOUtils.createDirIfNotExists(IOUtils.RINGTONES_DIR);
    }

    private void applyOverlays() {
        Log.d(TAG, "Applying theme overlays...");
        try {
            Context context = getApplicationContext();
            File themeDir = new File(IOUtils.THEME_CACHE_DIR);
            if (!themeDir.exists()) {
                themeDir.mkdirs();
            }
            try {
                String[] assets = context.getAssets().list("themes");
                if (assets != null) {
                    for (String asset : assets) {
                        InputStream in = context.getAssets().open("themes/" + asset);
                        File outFile = new File(themeDir, asset);
                        IOUtils.bufferedCopy(in, outFile);
                        in.close();
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error copying theme files: " + e.getMessage());
            }
            try {
                Object overlayManagerService = getSystemService("overlay");
                if (overlayManagerService != null) {
                    Class<?> overlayManagerClass = Class.forName("android.service.om.IOverlayManager");
                    Method setEnabled = overlayManagerClass.getMethod("setEnabled", String.class, boolean.class, int.class, int.class);
                    setEnabled.invoke(overlayManagerService, "com.example.theme.overlay", true, UserHandle.USER_CURRENT, 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error enabling overlay: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying overlays: " + e.getMessage());
        }
    }

    private void applySounds() {
        Log.d(TAG, "Applying sounds...");
        try {
            Context context = getApplicationContext();
            IOUtils.applyOverlaySounds();
            File ringtoneFile = new File(IOUtils.RINGTONES_DIR + "ringtone.ogg");
            if (ringtoneFile.exists()) {
                Uri ringtoneUri = Uri.fromFile(ringtoneFile);
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, ringtoneUri);
            }
            File notificationFile = new File(IOUtils.NOTIFICATIONS_DIR + "notification.ogg");
            if (notificationFile.exists()) {
                Uri notificationUri = Uri.fromFile(notificationFile);
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION, notificationUri);
            }
            File alarmFile = new File(IOUtils.ALARMS_DIR + "alarm.ogg");
            if (alarmFile.exists()) {
                Uri alarmUri = Uri.fromFile(alarmFile);
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM, alarmUri);
            }
            Settings.System.putString(context.getContentResolver(), Settings.System.LOCK_SOUND,
                IOUtils.UI_SOUNDS_DIR + "lock_sound.ogg");
            Settings.System.putString(context.getContentResolver(), Settings.System.UNLOCK_SOUND,
                IOUtils.UI_SOUNDS_DIR + "unlock_sound.ogg");
            Settings.System.putString(context.getContentResolver(), Settings.System.LOW_BATTERY_SOUND,
                IOUtils.UI_SOUNDS_DIR + "low_battery.ogg");
        } catch (Exception e) {
            Log.e(TAG, "Error applying sounds: " + e.getMessage());
        }
    }
}
