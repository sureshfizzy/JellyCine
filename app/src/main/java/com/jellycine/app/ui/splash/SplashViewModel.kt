package com.jellycine.app.ui.splash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _shouldShowSplash = MutableStateFlow(false)
    val shouldShowSplash: StateFlow<Boolean> = _shouldShowSplash.asStateFlow()
    
    companion object {
        private const val PREFS_NAME = "jellycine_splash_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_LAST_VERSION_CODE = "last_version_code"
    }
    
    init {
        checkSplashRequirement()
    }
    
    private fun checkSplashRequirement() {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentVersionCode = getCurrentVersionCode()
            val lastVersionCode = prefs.getInt(KEY_LAST_VERSION_CODE, -1)
            
            val shouldShow = when {
                lastVersionCode == -1 -> {
                    val isRecentInstall = isAppFreshlyInstalled()
                    isRecentInstall
                }
                currentVersionCode != lastVersionCode -> {
                    true
                }
                else -> {
                    false
                }
            }
            
            _shouldShowSplash.value = shouldShow
            
            prefs.edit()
                .putBoolean(KEY_FIRST_LAUNCH, false)
                .putInt(KEY_LAST_VERSION_CODE, currentVersionCode)
                .apply()
        }
    }
    
    private fun isAppFreshlyInstalled(): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installTime = packageInfo.firstInstallTime
            val updateTime = packageInfo.lastUpdateTime
            
            val currentTime = System.currentTimeMillis()
            val timeSinceInstall = currentTime - installTime
            val timeSinceUpdate = currentTime - updateTime
            
            val isRecentInstall = timeSinceInstall < 10000 || timeSinceUpdate < 10000
            
            isRecentInstall
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        } catch (e: Exception) {
            1
        }
    }
    
    fun onSplashComplete() {
        _shouldShowSplash.value = false
    }
}