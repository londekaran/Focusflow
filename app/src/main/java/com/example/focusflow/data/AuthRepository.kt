package com.example.focusflow.data

/**
 * The only door to authentication. Screens call this interface, never a provider SDK, so a real
 * implementation (Firebase, Google Sign-In, a backend) can replace [DummyAuthRepository] later.
 */
interface AuthRepository {
    /** [provider] is "email" or "google". Returns true on success. */
    fun signIn(email: String, provider: String): Boolean
    fun signUp(email: String, provider: String): Boolean
}

/** Dummy auth: remembers the email on the phone, checks nothing, sends nothing. */
class DummyAuthRepository(private val settings: SettingsStore) : AuthRepository {
    override fun signIn(email: String, provider: String): Boolean = remember(email, provider)
    override fun signUp(email: String, provider: String): Boolean = remember(email, provider)

    private fun remember(email: String, provider: String): Boolean {
        settings.accountEmail = email.trim()
        settings.accountProvider = provider
        return true
    }
}
