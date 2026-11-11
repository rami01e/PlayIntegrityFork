package es.chiteroman.playintegrityfix;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.JsonReader;
import android.util.Log;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class EntryPointVending {
    private static final Map<String, String> map = new HashMap<>();
    private static int verboseLogs = 0;

    private static void LOG(String msg) {
        Log.d("PIF/Java:PS", msg);
    }

    public static void receiveJson(String data) {
        try (JsonReader reader = new JsonReader(new StringReader(data))) {
            reader.beginObject();
            while (reader.hasNext()) {
                map.put(reader.nextName(), reader.nextString());
            }
            reader.endObject();
        } catch (IOException | IllegalStateException e) {
            LOG("Couldn't read JSON from Zygisk: " + e);
            map.clear();
            return;
        }
        if (verboseLogs > 0) LOG("Received " + map.size() + " Build Fields from Zygisk!");
    }

    @SuppressLint("DefaultLocale")
    public static void init(int verboseLogsVal, int spoofVendingFingerVal, int spoofVendingSdkVal, int spoofVendingBuildVal, String vendingFingerprintValue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("");
        }
        verboseLogs = verboseLogsVal;

        // Only spoof other fields to Play Store if not forcing Android <13 Play Integrity verdict
        if (spoofVendingSdkVal < 1) {
            // Spoof all Build Fields to Play Store so its device identity/integrity checks are consistent
            if (spoofVendingBuildVal > 0 && !map.isEmpty()) {
                for (String key : map.keySet()) {
                    setField(key, map.get(key));
                }
            }

            if (spoofVendingFingerVal < 1 && spoofVendingBuildVal < 1) return;

            // Custom or override FINGERPRINT (spoofVendingFinger may be set to a custom FINGERPRINT value)
            if (spoofVendingFingerVal > 0 && vendingFingerprintValue != null && !vendingFingerprintValue.isEmpty()
                    && !vendingFingerprintValue.equals(map.get("FINGERPRINT"))) {
                setField("FINGERPRINT", vendingFingerprintValue);
            }
        } else {
            spoofSdkInt(spoofVendingSdkVal);
        }
    }

    private static void spoofSdkInt(int spoofVendingSdkVal) {
        int requestSdk = spoofVendingSdkVal == 1 ? 32 : spoofVendingSdkVal;
        int targetSdk = Math.min(Build.VERSION.SDK_INT, requestSdk);
        int oldValue;
        try {
            Field field = Build.VERSION.class.getDeclaredField("SDK_INT");
            field.setAccessible(true);
            try {
                Field accessFlagsField = Field.class.getDeclaredField("accessFlags");
                accessFlagsField.setAccessible(true);
                accessFlagsField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                LOG("Couldn't modify accessFlags for SDK_INT: " + e);
            }
            oldValue = field.getInt(null);
            if (oldValue == targetSdk) {
                if (verboseLogs > 2) LOG(String.format("[SDK_INT]: %d (unchanged)", oldValue));
                field.setAccessible(false);
                return;
            }
            try {
                setFieldNative(field.getDeclaringClass(), field, field.getType().getName(), targetSdk);
            } catch (Exception e) {
                LOG("Native setField failed for SDK_INT: " + e);
            }
            field.setAccessible(false);
            LOG(String.format("[SDK_INT]: %d -> %d", oldValue, targetSdk));
        } catch (NoSuchFieldException e) {
            LOG("SDK_INT field not found: " + e);
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException |
                 NullPointerException | ExceptionInInitializerError e) {
            LOG("SDK_INT field not accessible: " + e);
        }
    }

    private static void setField(String name, String value) {
        if (value.isEmpty()) {
            LOG(String.format("%s is empty, skipping", name));
            return;
        }
        Field field;
        String oldValue;
        Object newValue;
        try {
            if (classContainsField(Build.class, name)) {
                field = Build.class.getDeclaredField(name);
            } else if (classContainsField(Build.VERSION.class, name)) {
                field = Build.VERSION.class.getDeclaredField(name);
            } else {
                if (verboseLogs > 1) LOG(String.format("Couldn't determine '%s' class name", name));
                return;
            }
        } catch (NoSuchFieldException e) {
            LOG(String.format("Couldn't find '%s' field name: " + e, name));
            return;
        }
        field.setAccessible(true);
        try {
            Field accessFlagsField = Field.class.getDeclaredField("accessFlags");
            accessFlagsField.setAccessible(true);
            accessFlagsField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOG(String.format("Couldn't modify accessFlags for '%s': " + e, name));
        }
        try {
            oldValue = String.valueOf(field.get(null));
        } catch (IllegalAccessException e) {
            LOG(String.format("Couldn't access '%s' field value: " + e, name));
            return;
        }
        if (value.equals(oldValue)) {
            if (verboseLogs > 2) LOG(String.format("[%s]: %s (unchanged)", name, value));
            return;
        }
        Class<?> fieldType = field.getType();
        if (fieldType == String.class) {
            newValue = value;
        } else if (fieldType == int.class) {
            newValue = Integer.parseInt(value);
        } else if (fieldType == long.class) {
            newValue = Long.parseLong(value);
        } else if (fieldType == boolean.class) {
            newValue = Boolean.parseBoolean(value);
        } else {
            LOG(String.format("Couldn't convert '%s' to '%s' type", value, fieldType));
            return;
        }
        try {
            setFieldNative(field.getDeclaringClass(), field, fieldType.getName(), newValue);
        } catch (Exception e) {
            LOG(String.format("Native setField failed for '%s': " + e, name));
        }
        LOG(String.format("[%s]: %s -> %s", name, oldValue, value));
    }

    private static boolean classContainsField(Class className, String fieldName) {
        for (Field field : className.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) return true;
        }
        return false;
    }

    private static native void setFieldNative(Class<?> targetClass, Field field, String type, Object value);
}
