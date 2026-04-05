package projekt.interfacer.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.UserHandle;
import de.robv.android.xposed.XposedHelpers;
import android.provider.Settings;
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
                        try (InputStream in = context.getAssets().open("themes/" + asset)) {
                            File outFile = new File(themeDir, asset);
                            IOUtils.bufferedCopy(in, outFile);
                        } catch (IOException e) {
                            Log.e(TAG, "Error copying theme file: " + asset + ", " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error listing theme assets: " + e.getMessage());
            }
            try {
                Object overlayManagerService = getSystemService("overlay");
                if (overlayManagerService != null) {
                    Class<?> overlayManagerClass = Class.forName("android.service.om.IOverlayManager");
                    Method getOverlayInfos = overlayManagerClass.getMethod("getOverlayInfos");
                    Method setEnabled = overlayManagerClass.getMethod("setEnabled", String.class, boolean.class, int.class);
                    Object overlayInfos = getOverlayInfos.invoke(overlayManagerService);
                    if (overlayInfos != null) {
                        java.util.List<?> overlayInfoList = (java.util.List<?>) overlayInfos;
                        for (Object overlayInfo : overlayInfoList) {
                            String packageName = (String) XposedHelpers.callMethod(overlayInfo, "getPackageName");
                            if (packageName != null) {
                                setEnabled.invoke(overlayManagerService, packageName, true, UserHandle.USER_CURRENT);
                                Log.d(TAG, "Enabled overlay: " + packageName);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error enabling overlays: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying overlays: " + e.getMessage());
        }
    }

    private void applySounds() {
        Log.d(TAG, "Applying sounds...");

        try {
            Context context = getApplicationContext();

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

            try {
                Settings.System.putString(context.getContentResolver(), "lock_sound",
                    IOUtils.UI_SOUNDS_DIR + "lock_sound.ogg");
                Settings.System.putString(context.getContentResolver(), "unlock_sound",
                    IOUtils.UI_SOUNDS_DIR + "unlock_sound.ogg");
                Settings.System.putString(context.getContentResolver(), "low_battery_sound",
                    IOUtils.UI_SOUNDS_DIR + "low_battery.ogg");
            } catch (Exception e) {
                Log.e(TAG, "Error setting UI sounds: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying sounds: " + e.getMessage());
        }
    }
}
