/*
 * Copyright (c) 2016-2017 Projekt Substratum
 * Xposed adaptation for Substratum-like theming (internal only)
 */

package projekt.interfacer.services;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.os.Binder;
import java.io.FileNotFoundException;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.IOException;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class IOUtils implements IXposedHookLoadPackage {
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
    private static final String MODULE_DATA_DIR = "/data/data/projekt.interfacer/";
    private static final String THEME_CACHE_DIR = MODULE_DATA_DIR + "theme/";
    private static final String FONTS_CACHE_DIR = THEME_CACHE_DIR + "fonts/";
    private static final String AUDIO_CACHE_DIR = THEME_CACHE_DIR + "audio/";
    private static final String UI_SOUNDS_DIR = AUDIO_CACHE_DIR + "ui/";
    private static final String ALARMS_DIR = AUDIO_CACHE_DIR + "alarms/";
    private static final String RINGTONES_DIR = AUDIO_CACHE_DIR + "ringtones/";
    private static final String NOTIFICATIONS_DIR = AUDIO_CACHE_DIR + "notifications/";
    public static final String SYSTEM_THEME_UI_SOUNDS_PATH = AUDIO_CACHE_DIR + "ui/";
    public static final String SYSTEM_THEME_ALARM_PATH = AUDIO_CACHE_DIR + "alarms/";
    public static final String SYSTEM_THEME_NOTIFICATION_PATH = AUDIO_CACHE_DIR + "notifications/";
    public static final String SYSTEM_THEME_RINGTONE_PATH = AUDIO_CACHE_DIR + "ringtones/";
    private static final String BOOTANIMATION_CACHE = THEME_CACHE_DIR + "bootanimation.zip";

    public static void createDirIfNotExists(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                Log.e(TAG, "Could not create directory: " + dirPath);
            }
        }
    }

    public static void bufferedCopy(InputStream in, File dest) {
        try (OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void copyFolder(File source, File dest) {
        if (!dest.exists()) {
            boolean created = dest.mkdirs();
            if (!created) {
                Log.e(TAG, "Could not create destination folder: " + dest.getAbsolutePath());
            }
        }

        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    File newFile = new File(dest, file.getName());
                    if (file.isFile()) {
                        bufferedCopy(file, newFile);
                    } else {
                        copyFolder(file, newFile);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error copying file: " + e);
                }
            }
        }
    }

    public static void unzip(File source, File destination) {
        try (ZipInputStream inputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(source)))) {
            ZipEntry zipEntry;
            int count;
            byte[] buffer = new byte[8192];

            while ((zipEntry = inputStream.getNextEntry()) != null) {
                File file = new File(destination, zipEntry.getName());
                File dir = zipEntry.isDirectory() ? file : file.getParentFile();

                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new RuntimeException("Failed to ensure directory: " + dir.getAbsolutePath());
                }

                if (zipEntry.isDirectory()) {
                    continue;
                }

                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    while ((count = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, count);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unzipping: " + e);
        }
    }

    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        boolean deleted = fileOrDirectory.delete();
        if (!deleted) {
            Log.e(TAG, "Could not delete file or directory: " + fileOrDirectory.getAbsolutePath());
        }
    }

    public static boolean dirExists(String dirPath) {
        File dir = new File(dirPath);
        return dir.exists() && dir.isDirectory();
    }

    private static List<Sound> SOUNDS = Arrays.asList(
        new Sound(AUDIO_CACHE_DIR + "ui/", "Effect_Tick", "Effect_Tick", RingtoneManager.TYPE_RINGTONE),
        new Sound(AUDIO_CACHE_DIR + "ui/", "lock_sound", "Lock"),
        new Sound(AUDIO_CACHE_DIR + "ui/", "unlock_sound", "Unlock"),
        new Sound(AUDIO_CACHE_DIR + "ui/", "low_battery_sound", "LowBattery"),
        new Sound(AUDIO_CACHE_DIR + "alarms/", "alarm", "alarm", RingtoneManager.TYPE_ALARM),
        new Sound(AUDIO_CACHE_DIR + "notifications/", "notification", "notification", RingtoneManager.TYPE_NOTIFICATION),
        new Sound(AUDIO_CACHE_DIR + "ringtones/", "ringtone", "ringtone", RingtoneManager.TYPE_RINGTONE)
    );

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("android")) return;
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
                        try {
                            Context overlayContext = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY);
                            if (enable) {
                                String[] fontFiles = overlayContext.getAssets().list("fonts");
                                if (fontFiles != null) {
                                    File fontsDir = new File(FONTS_CACHE_DIR);
                                    IOUtils.createDirIfNotExists(FONTS_CACHE_DIR);
                                    for (String fontFile : fontFiles) {
                                        InputStream in = overlayContext.getAssets().open("fonts/" + fontFile);
                                        File destFile = new File(fontsDir, fontFile);
                                        IOUtils.bufferedCopy(in, destFile);
                                        in.close();
                                    }
                                }
                            } else {
                                IOUtils.deleteRecursive(new File(FONTS_CACHE_DIR));
                            }
                            if (enable) {
                                String[] audioTypes = {"ui", "ringtones", "notifications", "alarms"};
                                for (String type : audioTypes) {
                                    String[] soundFiles = overlayContext.getAssets().list("audio/" + type);
                                    if (soundFiles != null) {
                                        File typeDir = new File(AUDIO_CACHE_DIR + type);
                                        IOUtils.createDirIfNotExists(typeDir.getAbsolutePath());
                                        for (String soundFile : soundFiles) {
                                            InputStream in = overlayContext.getAssets().open("audio/" + type + "/" + soundFile);
                                            File destFile = new File(typeDir, soundFile);
                                            IOUtils.bufferedCopy(in, destFile);
                                            in.close();
                                        }
                                    }
                                }
                            } else {
                                IOUtils.deleteRecursive(new File(AUDIO_CACHE_DIR));
                            }
                            if (enable) {
                                InputStream in = overlayContext.getAssets().open("bootanimation.zip");
                                File destFile = new File(BOOTANIMATION_CACHE);
                                IOUtils.bufferedCopy(in, destFile);
                                in.close();
                            } else {
                                new File(BOOTANIMATION_CACHE).delete();
                            }

                        } catch (Exception e) {
                            log("Error handling overlay files: " + e);
                        }
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
        String callingPackage = getPackageManager().getPackagesForUid(uid)[0];
        for (String AUTHORIZED_CALLER : AUTHORIZED_CALLERS) {
            if (TextUtils.equals(callingPackage, AUTHORIZED_CALLER)) {
                for (Signature AUTHORIZED_SIGNATURE : AUTHORIZED_SIGNATURES) {
                    if (doSignaturesMatch(callingPackage, AUTHORIZED_SIGNATURE)) {
                        log("\'" + callingPackage + "\' is an authorized calling package...");
                        return true;
                    }
                }
            }
        }
        log("\'" + callingPackage + "\' is not an authorized calling package.");
        return false;
    }

    private boolean doSignaturesMatch(String packageName, Signature signature) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            if (pi.signatures != null && pi.signatures.length == 1 && signature.equals(pi.signatures[0])) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void copyFolder(File source, File dest) {
        if (!dest.exists()) {
            boolean created = dest.mkdirs();
            if (!created) {
                log("Could not create destination folder: " + dest.getAbsolutePath());
            }
        }
        File[] files = source.listFiles();
        for (File file : files) {
            try {
                File newFile = new File(dest.getAbsolutePath() + File.separator + file.getName());
                if (file.isFile()) {
                    bufferedCopy(file, newFile);
                } else {
                    copyFolder(file, newFile);
                }
            } catch (Exception e) {
                log("Error copying file: " + e);
            }
        }
    }

    private static void unzip(String source, String destination) {
        try (ZipInputStream inputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(source)))) {
            ZipEntry zipEntry;
            int count;
            byte[] buffer = new byte[8192];
            while ((zipEntry = inputStream.getNextEntry()) != null) {
                File file = new File(destination, zipEntry.getName());
                File dir = zipEntry.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new FileNotFoundException("Failed to ensure directory: " + dir.getAbsolutePath());
                }
                if (zipEntry.isDirectory()) {
                    continue;
                }
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    while ((count = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, count);
                    }
                }
            }
        } catch (Exception e) {
            log("Error unzipping: " + e);
        }
    }

    private static class Sound {
        String themePath;
        String soundName;
        String soundPath;
        int type;

        Sound(String themePath, String soundName, String soundPath) {
            this.themePath = themePath;
            this.soundName = soundName;
            this.soundPath = soundPath;
        }

        Sound(String themePath, String soundName, String soundPath, int type) {
            this(themePath, soundName, soundPath);
            this.type = type;
        }
    }
}
