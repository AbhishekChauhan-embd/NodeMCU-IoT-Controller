package com.example.nodemcuiotcontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MqttCredentialStorage {

    private static final String TAG = "MqttCredentialStorage";

    private static final String PREF_NAME = "mqtt_secure_preferences";
    private static final String KEY_PASSWORD = "encrypted_password";
    private static final String KEY_IV = "encryption_iv";

    private static final String KEY_ALIAS = "NodeMCU_MQTT_KEY";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static SecretKey getSecretKey() throws Exception {

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );

        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT |
                        KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build();

        keyGenerator.init(keySpec);

        return keyGenerator.generateKey();
    }

    public static boolean savePassword(
            Context context,
            String password) {

        try {

            SecretKey secretKey = getSecretKey();

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encryptedBytes = cipher.doFinal(
                    password.getBytes(StandardCharsets.UTF_8)
            );

            byte[] iv = cipher.getIV();

            SharedPreferences preferences =
                    context.getSharedPreferences(
                            PREF_NAME,
                            Context.MODE_PRIVATE
                    );

            return preferences.edit()
                    .putString(
                            KEY_PASSWORD,
                            Base64.encodeToString(
                                    encryptedBytes,
                                    Base64.NO_WRAP
                            )
                    )
                    .putString(
                            KEY_IV,
                            Base64.encodeToString(
                                    iv,
                                    Base64.NO_WRAP
                            )
                    )
                    .commit();

        } catch (Exception e) {

            Log.e(TAG, "Unable to save MQTT credentials", e);

            return false;
        }
    }

    public static String getPassword(Context context) {

        try {

            SharedPreferences preferences =
                    context.getSharedPreferences(
                            PREF_NAME,
                            Context.MODE_PRIVATE
                    );

            String encryptedPassword =
                    preferences.getString(KEY_PASSWORD, null);

            String encodedIV =
                    preferences.getString(KEY_IV, null);

            if (encryptedPassword == null || encodedIV == null) {
                return null;
            }

            byte[] encryptedBytes = Base64.decode(
                    encryptedPassword,
                    Base64.NO_WRAP
            );

            byte[] iv = Base64.decode(
                    encodedIV,
                    Base64.NO_WRAP
            );

            SecretKey secretKey = getSecretKey();

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(128, iv);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    parameterSpec
            );

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(
                    decryptedBytes,
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            Log.e(TAG, "Unable to retrieve MQTT credentials", e);

            clearPassword(context);

            return null;
        }
    }

    public static boolean hasSavedPassword(Context context) {

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );

        return preferences.contains(KEY_PASSWORD);
    }

    public static void clearPassword(Context context) {

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );

        preferences.edit()
                .remove(KEY_PASSWORD)
                .remove(KEY_IV)
                .apply();

        try {

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");

            keyStore.load(null);

            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }

        } catch (Exception e) {

            Log.e(TAG, "Unable to clear encryption key", e);
        }
    }
}