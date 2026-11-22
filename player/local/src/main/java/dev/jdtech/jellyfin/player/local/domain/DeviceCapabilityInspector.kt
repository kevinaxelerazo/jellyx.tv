package dev.jdtech.jellyfin.player.local.domain

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class DeviceCapabilities(
    val supportsDolbyVision: Boolean,
    val supportsHdr10Plus: Boolean,
    val supportsHdr10: Boolean,
    val supports4kHevcHw: Boolean,
    val isLowRamDevice: Boolean,
    val totalRamMb: Long,
) {
    val isHighEndDevice: Boolean
        get() = !isLowRamDevice && totalRamMb >= 3500L && supports4kHevcHw
}

interface DeviceCapabilityInspector {
    fun inspect(): DeviceCapabilities
}

class AndroidDeviceCapabilityInspector
@Inject
constructor(@ApplicationContext private val context: Context) : DeviceCapabilityInspector {
    override fun inspect(): DeviceCapabilities {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        val hdrTypes = display?.hdrCapabilities?.supportedHdrTypes?.toSet() ?: emptySet()

        val supportsDolbyVision = Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdrTypes
        val supportsHdr10Plus =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in hdrTypes
            } else {
                false
            }
        val supportsHdr10 = Display.HdrCapabilities.HDR_TYPE_HDR10 in hdrTypes

        val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        var hasDolbyVisionHw = false
        var has4kHevcHw = false

        for (info in codecInfos) {
            if (info.isEncoder) continue
            val isHardware =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    !info.isSoftwareOnly
                } else {
                    !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                        !info.name.startsWith("c2.android.", ignoreCase = true)
                }
            if (!isHardware) continue

            for (type in info.supportedTypes) {
                if (type.equals("video/dolby-vision", ignoreCase = true)) {
                    hasDolbyVisionHw = true
                }
                if (type.equals("video/hevc", ignoreCase = true)) {
                    val caps = runCatching {
                        info.getCapabilitiesForType(type).videoCapabilities
                    }
                        .getOrNull()
                    if (caps == null || caps.isSizeSupported(3840, 2160)) {
                        has4kHevcHw = true
                    }
                }
            }
        }

        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024L * 1024L)
        val isLowRamDevice = activityManager?.isLowRamDevice ?: false

        return DeviceCapabilities(
            supportsDolbyVision = supportsDolbyVision && hasDolbyVisionHw,
            supportsHdr10Plus = supportsHdr10Plus,
            supportsHdr10 = supportsHdr10,
            supports4kHevcHw = has4kHevcHw,
            isLowRamDevice = isLowRamDevice,
            totalRamMb = totalRamMb,
        )
    }
}
