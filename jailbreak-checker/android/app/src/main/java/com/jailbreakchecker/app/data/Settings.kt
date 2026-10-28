package com.jailbreakchecker.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AppSettings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    /** 0 = automatic checks off. */
    val intervalHours: Int = 1,
    val notifications: Boolean = true,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        /** The cheapest model: the user asked to keep costs down. */
        const val DEFAULT_MODEL = "claude-haiku-5-5"
        val MODELS = listOf(
            "claude-haiku-5-5" to "Claude Haiku 5.5 · la más barata (recomendada)",
            "claude-sonnet-5-5" to "Claude Sonnet 5.5 · más precisa",
            "claude-opus-5-5" to "Claude Opus 5.5 · la más precisa",
        )
        val INTERVALS = listOf(1, 3, 6, 12, 24, 0)
    }
}

/** User settings. The Anthropic API key is encrypted with a key kept in the Android Keystore. */
class SettingsStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        // v3: switch the old Opus default to Haiku, which costs ~40× less.
        if (prefs.getInt(KEY_VERSION, 0) < 3) {
            if (prefs.getString(KEY_MODEL, null) == "claude-opus-5-5") prefs.edit().putString(KEY_MODEL, AppSettings.DEFAULT_MODEL).apply()
            prefs.edit().putInt(KEY_VERSION, 3).apply()
            _settings.value = load()
        }
    }

    private fun load() = AppSettings(
        apiKey = prefs.getString(KEY_API, null)?.let { runCatching { KeyCrypto.decrypt(it) }.getOrNull() }.orEmpty(),
        model = prefs.getString(KEY_MODEL, null) ?: AppSettings.DEFAULT_MODEL,
        intervalHours = prefs.getInt(KEY_INTERVAL, 1),
        notifications = prefs.getBoolean(KEY_NOTIFY, true),
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_API, settings.apiKey.trim().takeIf { it.isNotEmpty() }?.let { KeyCrypto.encrypt(it) })
            .putString(KEY_MODEL, settings.model)
            .putInt(KEY_INTERVAL, settings.intervalHours)
            .putBoolean(KEY_NOTIFY, settings.notifications)
            .apply()
        _settings.value = settings.copy(apiKey = settings.apiKey.trim())
    }

    companion object {
        private const val KEY_API = "api_key_enc"
        private const val KEY_MODEL = "model"
        private const val KEY_INTERVAL = "interval_hours"
        private const val KEY_NOTIFY = "notifications"
        private const val KEY_VERSION = "settings_version"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) { instance ?: SettingsStore(context).also { instance = it } }
    }
}

private object KeyCrypto {
    private const val ALIAS = "jailbreak_checker_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }
}
