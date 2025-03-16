package expo.modules.torch

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import android.util.Log

class ExpoTorchModule : Module() {
  private data class CameraInfo(
    val cameraId: String,
    val characteristics: CameraCharacteristics
  )

  private fun findTorchCamera(cameraManager: CameraManager): CameraInfo? {
    try {
      for (cameraId in cameraManager.cameraIdList) {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
        val isBackCamera = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        
        if (hasFlash && isBackCamera) {
          return CameraInfo(cameraId, characteristics)
        }
      }
    } catch (e: CameraAccessException) {
      Log.e("ExpoTorchModule", "Failed to access camera: ${e.message}")
    }
    return null
  }

  override fun definition() = ModuleDefinition {
    Name("ExpoTorch")

    Constants(
      "ON" to "ON",
      "OFF" to "OFF"
    )

    AsyncFunction("isTorchAvailable") { promise: Promise ->
      val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: run {
        promise.resolve(false)
        return@AsyncFunction
      }

      promise.resolve(findTorchCamera(cameraManager) != null)
    }

    AsyncFunction("isBrightnessControllable") { promise: Promise ->
      val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: run {
        promise.resolve(false)
        return@AsyncFunction
      }

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        promise.resolve(false)
        return@AsyncFunction
      }

      val cameraInfo = findTorchCamera(cameraManager)
      if (cameraInfo == null) {
        promise.resolve(false)
        return@AsyncFunction
      }

      val maxLevel = cameraInfo.characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
      promise.resolve(maxLevel != null && maxLevel > 1)
    }

    AsyncFunction("setStateAsync") { state: String, promise: Promise ->
      val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: run {
        promise.reject("E_CAMERA_MANAGER_UNAVAILABLE", "CameraManager is not available.", null)
        return@AsyncFunction
      }

      if (state != "ON" && state != "OFF") {
        promise.reject("E_INVALID_STATE", "Invalid state: $state. Use 'ON' or 'OFF'.", null)
        return@AsyncFunction
      }

      val cameraInfo = findTorchCamera(cameraManager)
      if (cameraInfo == null) {
        promise.reject("E_TORCH_UNAVAILABLE", "Torch is not available on this device.", null)
        return@AsyncFunction
      }

      try {
        cameraManager.setTorchMode(cameraInfo.cameraId, state == "ON")
        promise.resolve(null)
      } catch (e: CameraAccessException) {
        if (e.reason == CameraAccessException.CAMERA_IN_USE) {
          Log.e("ExpoTorchModule", "Torch is not available because the camera is in use by another application.")
          promise.reject("E_CAMERA_IN_USE", "Torch is not available because the camera is in use.", e)
        } else {
          promise.reject("E_TORCH_FAILURE", "Failed to set torch state: ${e.message}", e)
        }
      }
    }

    AsyncFunction("setBrightnessAsync") { level: Double, promise: Promise ->
      val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: run {
        promise.reject("E_CAMERA_MANAGER_UNAVAILABLE", "CameraManager is not available.", null)
        return@AsyncFunction
      }

      val cameraInfo = findTorchCamera(cameraManager)
      if (cameraInfo == null) {
        promise.reject("E_TORCH_UNAVAILABLE", "Torch is not available on this device.", null)
        return@AsyncFunction
      }

      try {
        if (level <= 0) {
          cameraManager.setTorchMode(cameraInfo.cameraId, false)
          promise.resolve(null)
          return@AsyncFunction
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          val maxLevel = cameraInfo.characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
          val strengthLevel = (level * maxLevel).toInt().coerceIn(1, maxLevel)
          cameraManager.turnOnTorchWithStrengthLevel(cameraInfo.cameraId, strengthLevel)
        } else {
          cameraManager.setTorchMode(cameraInfo.cameraId, level > 0)
        }
        
        promise.resolve(null)
      } catch (e: CameraAccessException) {
        if (e.reason == CameraAccessException.CAMERA_IN_USE) {
          Log.e("ExpoTorchModule", "Torch is not available because the camera is in use by another application.")
          promise.reject("E_CAMERA_IN_USE", "Torch is not available because the camera is in use.", e)
        } else {
          promise.reject("E_TORCH_FAILURE", "Failed to set torch brightness: ${e.message}", e)
        }
      }
    }
  }

  private val context
    get() = requireNotNull(appContext.reactContext) { "Context is not available." }
}
