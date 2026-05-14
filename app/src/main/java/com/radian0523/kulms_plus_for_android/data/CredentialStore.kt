package com.radian0523.kulms_plus_for_android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ECS-ID/パスワードを Android Keystore で暗号化して SharedPreferences に保存する。
 *
 * 暗号化方式: AES/GCM (鍵は AndroidKeyStore で管理)
 */
object CredentialStore {
    private const val PREFS_NAME = "kulms_credentials"
    private const val KEY_ALIAS = "kulms_credential_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private const val PREF_USERNAME_DATA = "username_data"
    private const val PREF_USERNAME_IV = "username_iv"
    private const val PREF_PASSWORD_DATA = "password_data"
    private const val PREF_PASSWORD_IV = "password_iv"
    private const val PREF_TOTP_SECRET_DATA = "totp_secret_data"
    private const val PREF_TOTP_SECRET_IV = "totp_secret_iv"

    /** ユーザー名とパスワードを暗号化して保存する。 */
    fun save(context: Context, username: String, password: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val (uData, uIv) = encrypt(username)
        val (pData, pIv) = encrypt(password)
        prefs.edit()
            .putString(PREF_USERNAME_DATA, uData)
            .putString(PREF_USERNAME_IV, uIv)
            .putString(PREF_PASSWORD_DATA, pData)
            .putString(PREF_PASSWORD_IV, pIv)
            .apply()
    }

    /** 保存された認証情報を返す。未保存または復号失敗時は null。 */
    fun load(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uData = prefs.getString(PREF_USERNAME_DATA, null) ?: return null
        val uIv = prefs.getString(PREF_USERNAME_IV, null) ?: return null
        val pData = prefs.getString(PREF_PASSWORD_DATA, null) ?: return null
        val pIv = prefs.getString(PREF_PASSWORD_IV, null) ?: return null
        return try {
            val username = decrypt(uData, uIv)
            val password = decrypt(pData, pIv)
            username to password
        } catch (e: Exception) {
            null
        }
    }

    // MARK: - TOTP Secret

    /** TOTP シークレットキーを暗号化して保存する。 */
    fun saveTotpSecret(context: Context, secret: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val (data, iv) = encrypt(secret)
        prefs.edit()
            .putString(PREF_TOTP_SECRET_DATA, data)
            .putString(PREF_TOTP_SECRET_IV, iv)
            .apply()
    }

    /** 保存された TOTP シークレットキーを返す。未保存または復号失敗時は null。 */
    fun loadTotpSecret(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val data = prefs.getString(PREF_TOTP_SECRET_DATA, null) ?: return null
        val iv = prefs.getString(PREF_TOTP_SECRET_IV, null) ?: return null
        return try {
            decrypt(data, iv)
        } catch (e: Exception) {
            null
        }
    }

    /** TOTP シークレットキーを削除する。 */
    fun clearTotpSecret(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(PREF_TOTP_SECRET_DATA)
            .remove(PREF_TOTP_SECRET_IV)
            .apply()
    }

    /** 認証情報が保存されているかを判定する（復号は試みない）。 */
    fun hasCredentials(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(PREF_USERNAME_DATA) && prefs.contains(PREF_PASSWORD_DATA)
    }

    /** 保存済み認証情報（ID/パスワード）を削除する。TOTP シークレットはログアウトしても保持する。 */
    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(PREF_USERNAME_DATA)
            .remove(PREF_USERNAME_IV)
            .remove(PREF_PASSWORD_DATA)
            .remove(PREF_PASSWORD_IV)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(plain: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to
            Base64.encodeToString(iv, Base64.NO_WRAP)
    }

    private fun decrypt(dataBase64: String, ivBase64: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val decrypted = cipher.doFinal(Base64.decode(dataBase64, Base64.NO_WRAP))
        return String(decrypted, Charsets.UTF_8)
    }
}
