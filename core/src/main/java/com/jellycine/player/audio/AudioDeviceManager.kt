package com.jellycine.player.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Audio device information
 */
data class ExternalAudioDevice(
    val name: String,
    val type: String,
    val supportedCodecs: List<String>,
    val maxChannels: Int,
    val sampleRates: List<Int>,
    val supportsHighRes: Boolean,
    val isConnected: Boolean
)

/**
 * Manager for detecting and monitoring external audio devices
 */
class AudioDeviceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioDeviceManager"
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var deviceReceiver: BroadcastReceiver? = null
    
    private val _connectedDevices = MutableStateFlow<List<ExternalAudioDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ExternalAudioDevice>> = _connectedDevices.asStateFlow()
    
    /**
     * Start monitoring audio device connections
     */
    fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            detectConnectedDevices()
            
            // Set up broadcast receiver for device changes
            deviceReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        AudioManager.ACTION_HEADSET_PLUG,
                        AudioManager.ACTION_AUDIO_BECOMING_NOISY,
                        Intent.ACTION_HEADSET_PLUG -> {
                            detectConnectedDevices()
                        }
                    }
                }
            }
            
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(Intent.ACTION_HEADSET_PLUG)
            }
            
            context.registerReceiver(deviceReceiver, filter)
            Log.d(TAG, "Started monitoring audio device connections")
        }
    }
    
    /**
     * Stop monitoring audio device connections
     */
    fun stopMonitoring() {
        deviceReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
                deviceReceiver = null
                Log.d(TAG, "Stopped monitoring audio device connections")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister device receiver: ${e.message}")
            }
        }
    }
    
    /**
     * Detect currently connected external audio devices
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun detectConnectedDevices() {
        try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val externalDevices = mutableListOf<ExternalAudioDevice>()
            
            devices.forEach { device ->
                if (isExternalDevice(device)) {
                    val deviceInfo = analyzeDevice(device)
                    externalDevices.add(deviceInfo)
                    Log.d(TAG, "Detected external device: ${deviceInfo.name} (${deviceInfo.type})")
                }
            }
            
            _connectedDevices.value = externalDevices
            Log.d(TAG, "Found ${externalDevices.size} external audio devices")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting audio devices: ${e.message}")
            _connectedDevices.value = emptyList()
        }
    }
    
    /**
     * Check if device is an external audio device (not built-in speaker)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun isExternalDevice(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_USB_DEVICE -> true
            else -> false
        }
    }
    
    /**
     * Analyze device capabilities and supported codecs
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun analyzeDevice(device: AudioDeviceInfo): ExternalAudioDevice {
        val deviceName = getDeviceName(device)
        val deviceType = getDeviceType(device)
        val supportedCodecs = getSupportedCodecs(device)
        val maxChannels = getMaxChannels(device)
        val sampleRates = getSupportedSampleRates(device)
        val supportsHighRes = isHighResCapable(device)
        
        return ExternalAudioDevice(
            name = deviceName,
            type = deviceType,
            supportedCodecs = supportedCodecs,
            maxChannels = maxChannels,
            sampleRates = sampleRates,
            supportsHighRes = supportsHighRes,
            isConnected = true
        )
    }
    
    /**
     * Get human-readable device name
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getDeviceName(device: AudioDeviceInfo): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            device.productName?.toString() ?: getDefaultDeviceName(device)
        } else {
            getDefaultDeviceName(device)
        }
    }
    
    /**
     * Get default device name based on type
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getDefaultDeviceName(device: AudioDeviceInfo): String {
        return when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Headphones"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI Audio"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device"
            else -> "External Audio Device"
        }
    }
    
    /**
     * Get device type string
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getDeviceType(device: AudioDeviceInfo): String {
        return when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
            else -> "External"
        }
    }
    
    /**
     * Get supported audio codecs for the device
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedCodecs(device: AudioDeviceInfo): List<String> {
        val codecs = mutableListOf<String>()

        codecs.add("PCM")
        codecs.add("AAC")
        
        when (device.type) {
            AudioDeviceInfo.TYPE_HDMI -> {
                codecs.addAll(listOf(
                    "Dolby Digital",
                    "Dolby Digital+",
                    "DTS",
                    "DTS-HD",
                    "Dolby TrueHD",
                    "Dolby Atmos"
                ))
            }
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> {
                codecs.addAll(listOf(
                    "FLAC",
                    "DSD",
                    "Hi-Res PCM"
                ))

                if (isHighResCapable(device)) {
                    codecs.addAll(listOf(
                        "Dolby Digital",
                        "DTS"
                    ))
                }
            }
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                codecs.addAll(getBluetoothCodecs(device))
            }
            else -> {
                codecs.add("MP3")
            }
        }
        
        return codecs.distinct().sorted()
    }
    
    /**
     * Get Bluetooth-specific codec support
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getBluetoothCodecs(device: AudioDeviceInfo): List<String> {
        val codecs = mutableListOf("SBC")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val deviceName = device.productName?.toString()?.lowercase() ?: ""

            when {
                deviceName.contains("ldac") -> codecs.add("LDAC")
                deviceName.contains("aptx") -> {
                    codecs.add("aptX")
                    if (deviceName.contains("hd")) codecs.add("aptX HD")
                    if (deviceName.contains("adaptive")) codecs.add("aptX Adaptive")
                }
                deviceName.contains("sony") -> codecs.add("LDAC")
                deviceName.contains("bose") -> codecs.add("aptX")
                deviceName.contains("sennheiser") -> codecs.add("aptX")
            }
        }
        
        return codecs
    }
    
    /**
     * Get maximum supported channels
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getMaxChannels(device: AudioDeviceInfo): Int {
        return when (device.type) {
            AudioDeviceInfo.TYPE_HDMI -> 8
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET -> {
                device.channelMasks?.maxOrNull()?.let { mask ->
                    Integer.bitCount(mask)
                } ?: 2
            }
            else -> 2
        }
    }
    
    /**
     * Get supported sample rates
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedSampleRates(device: AudioDeviceInfo): List<Int> {
        val rates = device.sampleRates?.toList() ?: listOf(44100, 48000)
        return rates.sorted()
    }
    
    /**
     * Check if device supports high-resolution audio
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun isHighResCapable(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_HDMI -> true
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET -> {
                device.sampleRates?.any { it >= 96000 } ?: false
            }
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val deviceName = device.productName?.toString()?.lowercase() ?: ""
                    deviceName.contains("ldac") || deviceName.contains("aptx")
                } else false
            }
            else -> false
        }
    }
    
    /**
     * Get current connected device count
     */
    fun getConnectedDeviceCount(): Int {
        return _connectedDevices.value.size
    }
    
    /**
     * Check if any external devices are connected
     */
    fun hasExternalDevices(): Boolean {
        return _connectedDevices.value.isNotEmpty()
    }
}