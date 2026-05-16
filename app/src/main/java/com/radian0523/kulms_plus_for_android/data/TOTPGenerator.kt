package com.radian0523.kulms_plus_for_android.data

import android.net.Uri
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP コードを生成するユーティリティ。
 * 外部依存なし（javax.crypto のみ使用）。
 */
object TOTPGenerator {
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Base32 シークレットから 6 桁の TOTP コードを生成する。
     */
    fun generate(secret: String): String? {
        val key = base32Decode(secret) ?: return null

        // 現在の UNIX 時間を 30 秒ステップに変換（RFC 6238 デフォルト）
        val counter = System.currentTimeMillis() / 1000 / 30
        val counterBytes = ByteArray(8)
        var c = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (c and 0xff).toByte()
            c = c shr 8
        }

        // HMAC-SHA1
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hmac = mac.doFinal(counterBytes)

        // Dynamic Truncation (RFC 4226 Section 5.4)
        val offset = (hmac[19].toInt() and 0x0f)
        val code = ((hmac[offset].toInt() and 0x7f) shl 24) or
            ((hmac[offset + 1].toInt() and 0xff) shl 16) or
            ((hmac[offset + 2].toInt() and 0xff) shl 8) or
            (hmac[offset + 3].toInt() and 0xff)
        val otp = code % 1_000_000

        return String.format("%06d", otp)
    }

    /**
     * otpauth:// URI から secret パラメータを抽出する。
     */
    fun extractSecret(otpauthUri: String): String? {
        if (!otpauthUri.startsWith("otpauth://")) return null
        val uri = Uri.parse(otpauthUri)
        val secret = uri.getQueryParameter("secret") ?: return null
        val cleaned = secret.replace(" ", "").replace("-", "").uppercase()
        if (!isValidBase32(cleaned)) return null
        return cleaned
    }

    /**
     * Base32 バリデーション（保存前チェック用）。
     */
    fun isValidBase32(input: String): Boolean {
        val cleaned = input.replace(" ", "").replace("-", "").uppercase()
        if (cleaned.isEmpty()) return false
        return cleaned.all { it in BASE32_ALPHABET || it == '=' }
    }

    private fun base32Decode(input: String): ByteArray? {
        val cleaned = input.replace(" ", "").replace("-", "").replace("=", "").uppercase()
        if (cleaned.isEmpty()) return null

        val output = mutableListOf<Byte>()
        var buffer = 0L
        var bitsLeft = 0

        for (char in cleaned) {
            val index = BASE32_ALPHABET.indexOf(char)
            if (index < 0) return null
            buffer = (buffer shl 5) or index.toLong()
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xff).toByte())
            }
        }

        return output.toByteArray()
    }
}
