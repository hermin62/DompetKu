package com.example.util

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    private const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(ALLOWED_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun getBiometricStatusMessage(context: Context): String {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(ALLOWED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Sensor biometrik siap digunakan"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Perangkat ini tidak memiliki sensor biometrik"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Sensor biometrik sedang tidak tersedia"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Belum ada sidik jari atau wajah yang didaftarkan di pengaturan perangkat"
            else -> "Biometrik belum tersedia pada perangkat ini"
        }
    }

    fun findFragmentActivity(context: Context): FragmentActivity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is FragmentActivity) return current
            current = current.baseContext
        }
        return current as? FragmentActivity
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Autentikasi DompetKu",
        subtitle: String = "Gunakan sidik jari atau biometrik untuk membuka data keuangan",
        negativeButtonText: String = "Gunakan PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isBiometricAvailable(activity)) {
            onError(getBiometricStatusMessage(activity))
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    val message = when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> "Autentikasi biometrik dibatalkan"
                        BiometricPrompt.ERROR_LOCKOUT -> "Terlalu banyak percobaan. Tunggu 30 detik atau gunakan PIN."
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Sensor biometrik terkunci sementara. Silakan gunakan PIN."
                        else -> errString.toString().ifBlank { "Autentikasi biometrik gagal" }
                    }
                    onError(message)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometrik tidak dikenali, coba tempelkan ulang")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
