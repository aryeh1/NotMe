package com.example.notme.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Helper class to manage database encryption passphrase using Android Keystore.
 * Generates and securely stores the encryption key for SQLCipher.
 */
public class DatabaseEncryptionHelper {
    private static final String TAG = "DBEncryptionHelper";
    private static final String PREFS_NAME = "db_encryption_prefs";
    private static final String KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase";
    private static final String KEY_IV = "encryption_iv";
    private static final String KEY_ALIAS = "notme_db_encryption_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final int PASSPHRASE_LENGTH = 32; // 256 bits
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * Gets or creates the database encryption passphrase.
     * Uses Android Keystore to encrypt and store the passphrase securely.
     *
     * @param context Application context
     * @return The database encryption passphrase as a byte array
     */
    public static byte[] getOrCreatePassphrase(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Check if we already have an encrypted passphrase stored
        String encryptedPassphraseBase64 = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null);
        String ivBase64 = prefs.getString(KEY_IV, null);

        if (encryptedPassphraseBase64 != null && ivBase64 != null) {
            // Decrypt and return existing passphrase
            try {
                byte[] encryptedPassphrase = Base64.decode(encryptedPassphraseBase64, Base64.DEFAULT);
                byte[] iv = Base64.decode(ivBase64, Base64.DEFAULT);
                return decryptPassphrase(encryptedPassphrase, iv);
            } catch (Exception e) {
                Log.e(TAG, "Failed to decrypt existing passphrase, generating new one", e);
                // Fall through to generate a new passphrase
            }
        }

        // Generate new random passphrase
        byte[] passphrase = generateRandomPassphrase();

        // Encrypt and store it
        try {
            ensureKeystoreKey();
            byte[] iv = new byte[12]; // GCM standard IV size
            new SecureRandom().nextBytes(iv);

            byte[] encryptedPassphrase = encryptPassphrase(passphrase, iv);

            prefs.edit()
                .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedPassphrase, Base64.DEFAULT))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.DEFAULT))
                .apply();

            Log.i(TAG, "Successfully generated and stored new encryption passphrase");
        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt and store passphrase", e);
        }

        return passphrase;
    }

    /**
     * Generates a cryptographically secure random passphrase.
     */
    private static byte[] generateRandomPassphrase() {
        byte[] passphrase = new byte[PASSPHRASE_LENGTH];
        new SecureRandom().nextBytes(passphrase);
        return passphrase;
    }

    /**
     * Ensures that the encryption key exists in Android Keystore.
     */
    private static void ensureKeystoreKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            );

            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();

            keyGenerator.init(keySpec);
            keyGenerator.generateKey();
            Log.i(TAG, "Generated new keystore key");
        }
    }

    /**
     * Encrypts the passphrase using the Android Keystore key.
     */
    private static byte[] encryptPassphrase(byte[] passphrase, byte[] iv) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

        return cipher.doFinal(passphrase);
    }

    /**
     * Decrypts the passphrase using the Android Keystore key.
     */
    private static byte[] decryptPassphrase(byte[] encryptedPassphrase, byte[] iv) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        return cipher.doFinal(encryptedPassphrase);
    }
}
