/*
 * Copyright (c) 2016-2017 Projekt Substratum
 * Xposed adaptation for Substratum-like theming
 */

package projekt.interfacer.services;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.media.RingtoneManager;
import android.net.Uri;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import projekt.interfacer.utils.IOUtils;
import projekt.interfacer.utils.SoundUtils;

public class JobService implements IXposedHookLoadPackage {
    private static final String TAG = "SubstratumXposed";
    private static final boolean DEBUG = true;
    private static final String SUBSTRATUM_PACKAGE = "projekt.substratum";
    private static final String INTERFACER_PACKAGE = "projekt.interfacer";
    private static final String[] AUTHORIZED_CALLERS = {INTERFACER_PACKAGE, SUBSTRATUM_PACKAGE};
    private static final Signature SUBSTRATUM_SIGNATURE = new Signature(""
            + "308202eb308201d3a003020102020411c02f2f300d06092a864886f70d01010b050030263124302206"
            + "03550403131b5375627374726174756d20446576656c6f706d656e74205465616d301e170d31363037"
            + "30333032333335385a170d3431303632373032333335385a3026312430220603550403131b53756273"
            + "74726174756d20446576656c6f706d656e74205465616d30820122300d06092a864886f70d01010105"
            + "000382010f003082010a02820101008855626336f645a335aa5d40938f15db911556385f72f72b5f8b"
            + "ad01339aaf82ae2d30302d3f2bba26126e8da8e76a834e9da200cdf66d1d5977c90a4e4172ce455704"
            + "a22bbe4a01b08478673b37d23c34c8ade3ec040a704da8570d0a17fce3c7397ea63ebcde3a2a3c7c5f"
            + "983a163e4cd5a1fc80c735808d014df54120e2e5708874739e22e5a22d50e1c454b2ae310b480825ab"
            + "3d877f675d6ac1293222602a53080f94e4a7f0692b627905f69d4f0bb1dfd647e281cc0695e0733fa3"
            + "efc57d88706d4426c4969aff7a177ac2d9634401913bb20a93b6efe60e790e06dad3493776c2c0878c"
            + "e82caababa183b494120edde3d823333efd464c8aea1f51f330203010001a321301f301d0603551d0e"
            + "04160414203ec8b075d1c9eb9d600100281c3924a831a46c300d06092a864886f70d01010b05000382"
            + "01010042d4bd26d535ce2bf0375446615ef5bf25973f61ecf955bdb543e4b6e6b5d026fdcab09fec09"
            + "c747fb26633c221df8e3d3d0fe39ce30ca0a31547e9ec693a0f2d83e26d231386ff45f8e4fd5c06095"
            + "8681f9d3bd6db5e940b1e4a0b424f5c463c79c5748a14a3a38da4dd7a5499dcc14a70ba82a50be5fe0"
            + "82890c89a27e56067d2eae952e0bcba4d6beb5359520845f1fdb7df99868786055555187ba46c69ee6"
            + "7fa2d2c79e74a364a8b3544997dc29cc625395e2f45bf8bdb2c9d8df0d5af1a59a58ad08b32cdbec38"
            + "19fa49201bb5b5aadeee8f2f096ac029055713b77054e8af07cd61fe97f7365d0aa92d570be98acb89"
            + "41b8a2b0053b54f18bfde092eb");
    private static final Signature[] AUTHORIZED_SIGNATURES = {SUBSTRATUM_SIGNATURE};

    private static List<Sound> SOUNDS = Arrays.asList(
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/", "Effect_Tick", "Effect_Tick", RingtoneManager.TYPE_RINGTONE),
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/", "lock_sound", "Lock"),
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/", "unlock_sound", "Unlock"),
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/", "low_battery_sound", "LowBattery"),
        new Sound(IOUtils.SYSTEM_THEME_ALARM_PATH, "/SoundsCache/alarms/", "alarm", "alarm", RingtoneManager.TYPE_ALARM),
        new Sound(IOUtils.SYSTEM_THEME_NOTIFICATION_PATH, "/SoundsCache/notifications/", "notification", "notification", RingtoneManager.TYPE_NOTIFICATION),
        new Sound(IOUtils.SYSTEM_THEME_RINGTONE_PATH, "/SoundsCache/ringtones/", "ringtone", "ringtone", RingtoneManager.TYPE_RINGTONE)
    );

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookInstallPackage(lpparam);
        hookUninstallPackage(lpparam);
        hookRestartSystemUI(lpparam);
        hookApplyBootanimation(lpparam);
        hookApplyFonts(lpparam);
        hookApplyAudio(lpparam);
        hookEnableOverlay(lpparam);
        hookDisableOverlay(lpparam);
        hookChangePriority(lpparam);
        hookCopy(lpparam);
        hookMove(lpparam);
        hookMkdir(lpparam);
        hookDeleteDirectory(lpparam);
        hookApplyProfile(lpparam);
        XposedHelpers.findAndHookMethod(
            "com.android.server.om.OverlayManagerService",
            lpparam.classLoader,
            "setEnabled",
            String.class, boolean.class, int.class, boolean.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String packageName = (String) param.args[0];
                    boolean enable = (boolean) param.args[1];
                    if (isCallerAuthorized(Binder.getCallingUid())) {
                        log("Overlay " + packageName + " will be " + (enable ? "enabled" : "disabled"));
                        handleOverlayFonts(packageName, enable);
                        handleOverlaySounds(packageName, enable);
                        handleOverlayBootanimation(packageName, enable);
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            "com.android.server.pm.PackageManagerService",
            lpparam.classLoader,
            "installPackageAsUser",
            String.class, int.class, String.class, int.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String packageName = (String) param.args[0];
                    if (isCallerAuthorized(Binder.getCallingUid())) {
                        log("Package " + packageName + " installed");
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            "com.android.server.pm.PackageManagerService",
            lpparam.classLoader,
            "deletePackageAsUser",
            String.class, int.class, int.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String packageName = (String) param.args[0];
                    if (isCallerAuthorized(Binder.getCallingUid())) {
                        log("Package " + packageName + " deleted");
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            "android.graphics.Typeface",
            lpparam.classLoader,
            "recreateDefaults",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (isCallerAuthorized(Binder.getCallingUid())) {
                        log("Fonts refreshed");
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            "android.media.RingtoneManager",
            lpparam.classLoader,
            "setActualDefaultRingtoneUri",
            Context.class, int.class, Uri.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isCallerAuthorized(Binder.getCallingUid())) {
                        int type = (int) param.args[1];
                        Uri uri = (Uri) param.args[2];
                        log("Setting default ringtone for type " + type + " to " + uri);
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            "com.android.server.am.ActivityManagerService",
            lpparam.classLoader,
            "killBackgroundProcesses",
            String.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String processName = (String) param.args[0];
                    if ("com.android.systemui".equals(processName) && isCallerAuthorized(Binder.getCallingUid())) {
                        log("Restarting SystemUI");
                    }
                }
            }
        );
    }

    private static void log(String msg) {
        if (DEBUG) XposedBridge.log(TAG + ": " + msg);
    }

    private boolean isCallerAuthorized(int uid) {
        Context context = AndroidAppHelper.currentApplication();
        if (context == null) return false;
        String[] packages = context.getPackageManager().getPackagesForUid(uid);
        if (packages == null || packages.length == 0) return false;
        String callingPackage = packages[0];
        return SUBSTRATUM_PACKAGE.equals(callingPackage) || INTERFACER_PACKAGE.equals(callingPackage);
    }

    private static boolean doSignaturesMatch(Context context, String packageName, Signature signature) {
        if (context == null) return false;
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            if (pi.signatures != null && pi.signatures.length == 1 && signature.equals(pi.signatures[0])) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void handleOverlayFonts(String packageName, boolean enable) {
        if (!enable) {
            deleteRecursive(new File(SoundUtils.THEME_FONTS_DIR));
            return;
        }
        try {
            Context overlayContext = getAppContext(packageName);
            if (overlayContext == null) return;
            File fontsDir = new File(SoundUtils.THEME_FONTS_DIR);
            if (!fontsDir.exists()) fontsDir.mkdirs();
            String[] fontFiles = overlayContext.getAssets().list("fonts");
            if (fontFiles != null) {
                for (String fontFile : fontFiles) {
                    try (InputStream in = overlayContext.getAssets().open("fonts/" + fontFile);
                        OutputStream out = new FileOutputStream(new File(fontsDir, fontFile))) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                }
            }
            XposedHelpers.callStaticMethod(
                android.graphics.Typeface.class,
                "recreateDefaults"
            );
        } catch (Exception e) {
            log("Error handling overlay fonts: " + e);
        }
    }

    private Context getAppContext(String packageName) {
        try {
            Context context = AndroidAppHelper.currentApplication();
            return context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            log("Error getting overlay context: " + e);
            return null;
        }
    }

    private void deleteRecursive(File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            for (File child : fileOrDir.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDir.delete();
    }

    private void handleOverlayBootanimation(String packageName, boolean enable) {
        if (!enable) {
            new File(SoundUtils.BOOTANIMATION_CACHE).delete();
            return;
        }
        try {
            Context overlayContext = getAppContext(packageName);
            if (overlayContext == null) return;
            try (InputStream in = overlayContext.getAssets().open("bootanimation.zip");
                OutputStream out = new FileOutputStream(SoundUtils.BOOTANIMATION_CACHE)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("com.android.server.BootAnimation", null),
                "start"
            );
        } catch (Exception e) {
            log("Error handling overlay bootanimation: " + e);
        }
    }

    private void hookApplyProfile(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "com.android.server.om.OverlayManagerService",
            lpparam.classLoader,
            "setEnabled",
            String.class, boolean.class, int.class, boolean.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String packageName = (String) param.args[0];
                    log("Applying profile for overlay: " + packageName);
                }
            }
        );
    }

    private void hookDeleteDirectory(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "java.io.File",
            lpparam.classLoader,
            "delete",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    File dir = (File) param.thisObject;
                    log("Deleting directory: " + dir.getAbsolutePath());
                }
            }
        );
    }

    private void hookMkdir(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "java.io.File",
            lpparam.classLoader,
            "mkdirs",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    File dir = (File) param.thisObject;
                    log("Directory created: " + dir.getAbsolutePath());
                }
            }
        );
    }

    private void hookMove(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "java.io.File",
            lpparam.classLoader,
            "renameTo",
            File.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    File source = (File) param.thisObject;
                    File dest = (File) param.args[0];
                    log("Moving file from " + source.getAbsolutePath() + " to " + dest.getAbsolutePath());
                }
            }
        );
    }

    private void hookCopy(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "java.io.File",
            lpparam.classLoader,
            "renameTo",
            File.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    File source = (File) param.thisObject;
                    File dest = (File) param.args[0];
                    log("Copying file from " + source.getAbsolutePath() + " to " + dest.getAbsolutePath());
                }
            }
        );
    }

    private void hookChangePriority(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "com.android.server.om.OverlayManagerService",
            lpparam.classLoader,
            "setPriority",
            String.class, String.class, int.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String targetPackage = (String) param.args[0];
                    String parentPackage = (String) param.args[1];
                    log("Changing priority for overlay: " + targetPackage);
                }
            }
        );
    }

    private void hookDisableOverlay(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "com.android.server.om.OverlayManagerService",
            lpparam.classLoader,
            "setEnabled",
            String.class, boolean.class, int.class, boolean.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String packageName = (String) param.args[0];
                    boolean enable = (boolean) param.args[1];
                    if (!enable) {
                        log("Overlay disabled: " + packageName);
                    }
                }
            }
        );
    }

    private void hookEnableOverlay(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "com.android.server.om.OverlayManagerService",
            lpparam.classLoader,
            "setEnabled",
            String.class, boolean.class, int.class, boolean.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String packageName = (String) param.args[0];
                    boolean enable = (boolean) param.args[1];
                    if (enable) {
                        log("Overlay enabled: " + packageName);
                    }
                }
            }
        );
    }

    private void hookApplyAudio(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "android.media.RingtoneManager",
            lpparam.classLoader,
            "setActualDefaultRingtoneUri",
            Context.class, int.class, Uri.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    log("Applying audio");
                }
            }
        );
    }

    private void hookApplyFonts(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "android.graphics.Typeface",
            lpparam.classLoader,
            "recreateDefaults",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    log("Fonts applied");
                }
            }
        );
    }

    private void hookApplyBootanimation(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "com.android.server.BootAnimation",
            lpparam.classLoader,
            "start",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    log("Applying bootanimation");
                }
            }
        );
    }

    private void hookRestartSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "com.android.server.am.ActivityManagerService",
            lpparam.classLoader,
            "killBackgroundProcesses",
            String.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String processName = (String) param.args[0];
                    if ("com.android.systemui".equals(processName)) {
                        log("Restarting SystemUI");
                    }
                }
            }
        );
    }

    private void hookUninstallPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "com.android.server.pm.PackageManagerService",
            lpparam.classLoader,
            "deletePackageAsUser",
            String.class, int.class, int.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String packageName = (String) param.args[0];
                    log("Package uninstalled: " + packageName);
                }
            }
        );
    }

    private void hookInstallPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "com.android.server.pm.PackageManagerService",
            lpparam.classLoader,
            "installPackageAsUser",
            String.class, int.class, String.class, int.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String packagePath = (String) param.args[0];
                    log("Package installed: " + packagePath);
                }
            }
        );
    }

    private void handleOverlaySounds(String packageName, boolean enable) {
        if (!enable) {
            deleteRecursive(new File(SoundUtils.THEME_AUDIO_DIR));
            Context context = AndroidAppHelper.currentApplication();
            SoundUtils.setDefaultAudible(context, RingtoneManager.TYPE_RINGTONE);
            SoundUtils.setDefaultAudible(context, RingtoneManager.TYPE_NOTIFICATION);
            SoundUtils.setDefaultAudible(context, RingtoneManager.TYPE_ALARM);
            return;
        }
        try {
            Context overlayContext = getAppContext(packageName);
            if (overlayContext == null) return;
            File audioDir = new File(SoundUtils.THEME_AUDIO_DIR);
            if (!audioDir.exists()) audioDir.mkdirs();
            String[] audioTypes = {"ringtones", "notifications", "alarms", "ui"};
            for (String type : audioTypes) {
                String[] soundFiles = overlayContext.getAssets().list("audio/" + type);
                if (soundFiles != null) {
                    File typeDir = new File(audioDir, type);
                    if (!typeDir.exists()) typeDir.mkdirs();
                    for (String soundFile : soundFiles) {
                        try (InputStream in = overlayContext.getAssets().open("audio/" + type + "/" + soundFile);
                             OutputStream out = new FileOutputStream(new File(typeDir, soundFile))) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                        }
                    }
                }
            }
            applyOverlaySounds();
        } catch (Exception e) {
            log("Error handling overlay sounds: " + e);
        }
    }

    private void applyOverlaySounds() {
        File ringtone = new File(SoundUtils.RINGTONES_DIR + "ringtone.ogg");
        if (ringtone.exists()) {
            SoundUtils.setAudible(context, ringtone, RingtoneManager.TYPE_RINGTONE, "Substratum Ringtone");
        }

        File notification = new File(SoundUtils.NOTIFICATIONS_DIR + "notification.ogg");
        if (notification.exists()) {
            SoundUtils.setAudible(context, notification, RingtoneManager.TYPE_NOTIFICATION, "Substratum Notification");
        }
        File alarm = new File(SoundUtils.ALARMS_DIR + "alarm.ogg");
        if (alarm.exists()) {
            SoundUtils.setAudible(context, alarm, RingtoneManager.TYPE_ALARM, "Substratum Alarm");
        }
        SoundUtils.setUISounds(getApplicationContext().getContentResolver(), "lock_sound", SoundUtils.UI_SOUNDS_DIR + "lock_sound.ogg");
        SoundUtils.setUISounds(getApplicationContext().getContentResolver(), "unlock_sound", SoundUtils.UI_SOUNDS_DIR + "unlock_sound.ogg");
        SoundUtils.setUISounds(getApplicationContext().getContentResolver(), "low_battery_sound", SoundUtils.UI_SOUNDS_DIR + "low_battery.ogg");
    }

    private void copyFonts(String pid, String zipFileName) {
        try {
            File cacheDir = new File(Environment.getDataDirectory(), "cache/FontCache/");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File systemFonts = new File("/system/fonts");
            IOUtils.copyFolder(systemFonts, cacheDir);
        } catch (Exception e) {
            log("Error copying fonts: " + e);
        }
    }

    private void applyThemedSounds(String pid, String zipFileName) {
        try {
            File cacheDir = new File(Environment.getDataDirectory(), "cache/SoundsCache/");
            if (!cacheDir.exists()) cacheDir.mkdirs();
        } catch (Exception e) {
            log("Error applying sounds: " + e);
        }
    }

    private void copyBootAnimation(String fileName) {
        try {
            File source = new File(fileName);
            File dest = new File(IOUtils.SYSTEM_THEME_BOOTANIMATION_PATH);
            IOUtils.bufferedCopy(source, dest);
        } catch (Exception e) {
            log("Error copying bootanimation: " + e);
        }
    }

    private static class Sound {
        String themePath;
        String cachePath;
        String soundName;
        String soundPath;
        int type;

        Sound(String themePath, String cachePath, String soundName, String soundPath) {
            this.themePath = themePath;
            this.cachePath = cachePath;
            this.soundName = soundName;
            this.soundPath = soundPath;
        }

        Sound(String themePath, String cachePath, String soundName, String soundPath, int type) {
            this(themePath, cachePath, soundName, soundPath);
            this.type = type;
        }
    }
}
