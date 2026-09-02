package com.hinnka.mycamera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.Build
import com.hinnka.mycamera.utils.SystemPropertiesUtil
import kotlin.math.abs

/**
 * 物理可变光圈（硬体光圈叶片）控制，移植自 NX733J 官方相机
 * （com.android.camera 11.0.401，Pro 模式可变光圈）。
 *
 * HAL 契约（与官方相机 ParametersSetter.setVariAperParameters 完全一致）：
 *
 * ZTE 路径（SDK <= 35，NX733J 为 Android 15）：
 *   请求键 "com.zte.chi.aperture.level"（Int，1..9），自动模式 = 不下发该键；
 *   回读键 "com.zte.chi.aperture.level" / "com.zte.camera.aperture.level"（Int，1..9）。
 *
 * QCOM 路径（SDK > 35，对应官方 FunctionUtil.QCOM_VARIABLE = SDK_INT > 35）：
 *   请求键 "org.codeaurora.qcamera3.variableaperture.aperture_mode"（Int，0=自动，1=手动）
 *         "org.codeaurora.qcamera3.variableaperture.aperture_fnumber"（Float）
 *   自动模式 = 不下发这两个键。
 *
 * 光圈档位与 F 值对应（官方 VariableApertureView.mVariableApertureArray，索引 = level - 1）：
 *   1=f/1.59, 2=f/1.8, 3=f/2.0, 4=f/2.2, 5=f/2.5, 6=f/2.8, 7=f/3.2, 8=f/3.5, 9=f/4.0
 *
 * 设备能力判定与官方相机一致：优先看厂商特性系统属性
 * "ro.vendor.feature.camera_variable_aperture"（官方 Config.hasVariableAperture()），
 * 同时接受 HAL 在 availableCaptureRequestKeys 中显式声明请求键的情况。
 */
object VariableApertureControl {

    const val LEVEL_MIN = 1
    const val LEVEL_MAX = 9
    const val DEFAULT_LEVEL = 1

    const val ZTE_REQUEST_KEY_NAME = "com.zte.chi.aperture.level"
    const val ZTE_RESULT_KEY_NAME = "com.zte.chi.aperture.level"
    const val ZTE_RESULT_KEY_NAME_ALT = "com.zte.camera.aperture.level"
    const val QCOM_REQUEST_MODE_KEY_NAME = "org.codeaurora.qcamera3.variableaperture.aperture_mode"
    const val QCOM_REQUEST_F_NUMBER_KEY_NAME = "org.codeaurora.qcamera3.variableaperture.aperture_fnumber"

    private const val SYSTEM_PROPERTY_FEATURE = "ro.vendor.feature.camera_variable_aperture"
    private const val F_NUMBER_MATCH_TOLERANCE = 0.005f

    /**
     * 官方 VariableApertureView.mVariableApertureArray
     */
    val FNUMBERS: List<Float> = listOf(1.59f, 1.8f, 2.0f, 2.2f, 2.5f, 2.8f, 3.2f, 3.5f, 4.0f)

    private val zteRequestKey = CaptureRequest.Key(ZTE_REQUEST_KEY_NAME, Int::class.java)
    private val zteResultKey = CaptureResult.Key(ZTE_RESULT_KEY_NAME, Int::class.java)
    private val zteResultKeyAlt = CaptureResult.Key(ZTE_RESULT_KEY_NAME_ALT, Int::class.java)
    private val qcomRequestModeKey = CaptureRequest.Key(QCOM_REQUEST_MODE_KEY_NAME, Int::class.java)
    private val qcomRequestFNumberKey = CaptureRequest.Key(QCOM_REQUEST_F_NUMBER_KEY_NAME, Float::class.java)

    /**
     * 与官方 FunctionUtil.QCOM_VARIABLE 一致：仅 SDK > 35 时走 QCOM CHI 接口。
     */
    val useQcomPath: Boolean
        get() = Build.VERSION.SDK_INT > 35

    /**
     * 当前 SDK 路径下需要下发的请求键名（用于 HAL 能力声明检测）。
     */
    val requestKeyNames: List<String>
        get() = if (useQcomPath) {
            listOf(QCOM_REQUEST_MODE_KEY_NAME, QCOM_REQUEST_F_NUMBER_KEY_NAME)
        } else {
            listOf(ZTE_REQUEST_KEY_NAME)
        }

    /**
     * 档位 -> F 值（官方 getFNumberByLevel），非法档位返回 -1。
     */
    fun fNumberByLevel(level: Int): Float {
        return if (level in LEVEL_MIN..LEVEL_MAX) FNUMBERS[level - 1] else -1f
    }

    /**
     * F 值 -> 档位（官方 getLocation + 1），无法匹配返回 -1。
     */
    fun levelByFNumber(fNumber: Float): Int {
        val index = FNUMBERS.indexOfFirst { abs(it - fNumber) < F_NUMBER_MATCH_TOLERANCE }
        return if (index >= 0) index + 1 else -1
    }

    /**
     * 官方 Config.hasVariableAperture()：读取系统属性 ro.vendor.feature.camera_variable_aperture。
     */
    fun isFeatureEnabledBySystemProperty(): Boolean {
        val value = SystemPropertiesUtil.get(SYSTEM_PROPERTY_FEATURE) ?: return false
        return value == "true" || value == "1"
    }

    /**
     * HAL 是否在 availableCaptureRequestKeys 中声明了当前路径的光圈请求键。
     */
    fun isKeyExposed(characteristics: CameraCharacteristics?): Boolean {
        if (characteristics == null) return false
        return try {
            val availableNames = characteristics.availableCaptureRequestKeys.map { it.name }
            requestKeyNames.any { availableNames.contains(it) }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 设备是否支持物理可变光圈：系统属性或 HAL 键声明任一满足即视为支持。
     */
    fun isSupported(characteristics: CameraCharacteristics?): Boolean {
        return isFeatureEnabledBySystemProperty() || isKeyExposed(characteristics)
    }

    /**
     * 手动光圈：按当前 SDK 路径把请求键写入 builder。
     * 自动光圈不需要调用本方法——与官方相机一致，自动模式 = 不下发任何键。
     */
    fun applyManual(builder: CaptureRequest.Builder, level: Int) {
        if (level !in LEVEL_MIN..LEVEL_MAX) return
        if (useQcomPath) {
            builder.set(qcomRequestModeKey, 1)
            builder.set(qcomRequestFNumberKey, fNumberByLevel(level))
        } else {
            builder.set(zteRequestKey, level)
        }
    }

    /**
     * 从 CaptureResult 回读当前光圈档位（ZTE 回读键），不适用或未上报时返回 null。
     */
    fun readLevelFromResult(result: CaptureResult): Int? {
        return try {
            val level = result.get(zteResultKey) ?: result.get(zteResultKeyAlt)
            level?.takeIf { it in LEVEL_MIN..LEVEL_MAX }
        } catch (e: Exception) {
            null
        }
    }
}
