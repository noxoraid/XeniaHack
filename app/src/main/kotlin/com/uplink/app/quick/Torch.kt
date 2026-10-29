package com.uplink.app

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

object Torch {
    fun set(ctx: Context, on: Boolean): Boolean {
        val manager = ctx.getSystemService(CameraManager::class.java)
        val id = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        return runCatching { manager.setTorchMode(id, on) }.isSuccess
    }
}
