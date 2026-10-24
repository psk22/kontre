package com.reelshield.app.data.rules

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted local cache for dynamic heuristic rules.
 * Uses AES-256-GCM encryption with Android KeyStore-backed hardware security.
 * Prevents local tampering and obfuscates detection heuristics on-device.
 */
class EncryptedRuleCache(private val context: Context) {

    private val TAG = "EncryptedRuleCache"
    private val KEY_ALIAS = "kontre_dynamic_rules_key_v1"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val CACHE_FILE_NAME = "encrypted_heuristic_rules.bin"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts the rules using AES-GCM and persists them to private app storage.
     */
    fun saveRules(rules: DynamicHeuristicRules): Boolean {
        return try {
            val jsonString = rules.toJson()
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(jsonString.toByteArray(Charsets.UTF_8))

            // File format: [4 bytes IV length] + [IV bytes] + [Ciphertext bytes]
            val file = File(context.filesDir, CACHE_FILE_NAME)
            file.outputStream().use { out ->
                out.write(iv.size)
                out.write(iv)
                out.write(cipherBytes)
            }
            Log.i(TAG, "Dynamic rules v${rules.version} encrypted and cached successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save encrypted rules cache", e)
            false
        }
    }

    /**
     * Decrypts and deserializes dynamic heuristic rules from private storage.
     * Returns null if cache does not exist or decryption fails.
     */
    fun loadRules(): DynamicHeuristicRules? {
        val file = File(context.filesDir, CACHE_FILE_NAME)
        if (!file.exists()) return null

        return try {
            file.inputStream().use { input ->
                val ivSize = input.read()
                if (ivSize <= 0 || ivSize > 32) return null
                val iv = ByteArray(ivSize)
                input.read(iv)

                val cipherBytes = input.readBytes()
                val secretKey = getOrCreateSecretKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                val plainBytes = cipher.doFinal(cipherBytes)
                val jsonString = String(plainBytes, Charsets.UTF_8)
                DynamicHeuristicRules.fromJson(jsonString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load/decrypt rules cache, will fallback to defaults", e)
            try { file.delete() } catch (_: Exception) {}
            null
        }
    }

    fun clearCache() {
        try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear rules cache", e)
        }
    }
}
