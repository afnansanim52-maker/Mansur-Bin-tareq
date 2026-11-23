package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.i18n.AppLanguage
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.vitRemoteDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vitremote_prefs"
)

data class LocalUserIdentity(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val lastSignedInAtMs: Long
)

class LocalPreferencesRepository(private val context: Context) {

    private object Keys {
        val DEVICE_CODE = stringPreferencesKey("device_code")
        val LANGUAGE = stringPreferencesKey("language_code")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val USER_UID = stringPreferencesKey("saved_user_uid")
        val USER_DISPLAY_NAME = stringPreferencesKey("saved_user_display_name")
        val USER_EMAIL = stringPreferencesKey("saved_user_email")
        val USER_PHOTO_URL = stringPreferencesKey("saved_user_photo_url")
        val USER_LAST_SIGNED_IN_MS = longPreferencesKey("saved_user_last_signed_in_ms")
    }

    val languageFlow: Flow<AppLanguage> = context.vitRemoteDataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[Keys.LANGUAGE])
    }

    val darkThemeFlow: Flow<Boolean> = context.vitRemoteDataStore.data.map { prefs ->
        prefs[Keys.DARK_THEME] ?: true
    }

    val userIdentityFlow: Flow<LocalUserIdentity?> = context.vitRemoteDataStore.data.map { prefs ->
        val uid = prefs[Keys.USER_UID]
        if (uid.isNullOrBlank()) {
            null
        } else {
            LocalUserIdentity(
                uid = uid,
                displayName = prefs[Keys.USER_DISPLAY_NAME].orEmpty(),
                email = prefs[Keys.USER_EMAIL].orEmpty(),
                photoUrl = prefs[Keys.USER_PHOTO_URL]?.takeIf { it.isNotBlank() },
                lastSignedInAtMs = prefs[Keys.USER_LAST_SIGNED_IN_MS] ?: 0L
            )
        }
    }

    suspend fun saveUserIdentity(user: FirebaseUser) {
        saveUserIdentity(
            uid = user.uid,
            displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "VitRemote User",
            email = user.email.orEmpty(),
            photoUrl = user.photoUrl?.toString()
        )
    }

    suspend fun saveUserIdentity(
        uid: String,
        displayName: String,
        email: String,
        photoUrl: String?
    ) {
        context.vitRemoteDataStore.edit { prefs ->
            prefs[Keys.USER_UID] = uid
            prefs[Keys.USER_DISPLAY_NAME] = displayName
            prefs[Keys.USER_EMAIL] = email
            if (!photoUrl.isNullOrBlank()) {
                prefs[Keys.USER_PHOTO_URL] = photoUrl
            } else {
                prefs.remove(Keys.USER_PHOTO_URL)
            }
            prefs[Keys.USER_LAST_SIGNED_IN_MS] = System.currentTimeMillis()
        }
    }

    suspend fun clearUserIdentity() {
        context.vitRemoteDataStore.edit { prefs ->
            prefs.remove(Keys.USER_UID)
            prefs.remove(Keys.USER_DISPLAY_NAME)
            prefs.remove(Keys.USER_EMAIL)
            prefs.remove(Keys.USER_PHOTO_URL)
            prefs.remove(Keys.USER_LAST_SIGNED_IN_MS)
        }
    }

    suspend fun getOrCreateDeviceCode(): String {
        val existing = context.vitRemoteDataStore.data.first()[Keys.DEVICE_CODE]
        if (!existing.isNullOrBlank()) return existing
        val generated = generateSecureDeviceCode()
        context.vitRemoteDataStore.edit { prefs ->
            prefs[Keys.DEVICE_CODE] = generated
        }
        return generated
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.vitRemoteDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.code
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.vitRemoteDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = enabled
        }
    }

    private fun generateSecureDeviceCode(): String {
        val random = SecureRandom()
        val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        fun chunk(len: Int) = buildString(len) {
            repeat(len) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
        return "VR-${chunk(3)}-${chunk(3)}"
    }
}
