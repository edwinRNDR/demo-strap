package studio.rndr.camera

import com.google.gson.Gson
import org.openrndr.math.Quaternion
import org.openrndr.math.Vector3
import org.openrndr.math.slerp
import org.openrndr.math.smoothstep
import java.io.File


enum class TweenMode {
    LINEAR,
    EASE_IN_OUT,
    EASE_IN,
    EASE_OUT,
    STEP_START,
    STEP_END,
    STAIR_2,
    STAIR_4,
    STAIR_8,
    STAIR_16,
    STAIR_32,
    SMOOTH_STAIR_8,
    SMOOTH_STAIR_16,
    SMOOTH_STAIR_32,
    EXPO_STAIR_4,
    EXPO_STAIR_8,
    EXPO_STAIR_16,
    EXPONENTIAL_IN,
    EXPONENTIAL_OUT,


}
data class CameraKey(val position:Vector3, val orientation:Quaternion, val fov:Double, val aperture:Double, val focalPlane:Double, val exposure:Double, val centerX:Double, val centerY:Double, val time:Double, val tweenMode:TweenMode)

fun expoIn(x:Double):Double {
    return Math.pow(2.0, 10.0 * (x-1.0))
}

fun expoOut(x:Double):Double {
    return -Math.pow(2.0, -10.0 * x ) + 1.0
}

fun loadKeys(file: File):List<CameraKey> {
    val json = file.readText()
    val keys = Gson().fromJson(json, Array<CameraKey>::class.java).toList()
    return keys
}

class CameraKeyframer {
    val keys = mutableListOf<CameraKey>()

    companion object {
        fun fromFile(name:String):CameraKeyframer = CameraKeyframer().apply {
            keys.addAll(loadKeys(File(name)))
        }
    }

    fun frame(time:Double):CameraKey {

        if (keys.size == 0) {
            return CameraKey(Vector3.ZERO, Quaternion.IDENTITY, 90.0, 1.0, 100.0,  1.0, 0.0, 0.0,-1.0, TweenMode.LINEAR)
        }

        if (keys.size == 1) {
            return keys[0]
        }


        val rightIndex = if (time <= keys[0].time) 1 else keys.indexOfFirst {
            it.time > time
        }

        val leftIndex = rightIndex-1

        if (leftIndex >=0 && rightIndex >= 0) {

            val left = keys[leftIndex]
            val right = keys[rightIndex]

            val dt = right.time - left.time
            val d0 = Math.max(0.0,time-left.time)

            val px = d0/dt

            val x = when (right.tweenMode) {
                TweenMode.LINEAR -> px
                TweenMode.STEP_START -> 1.0
                TweenMode.STEP_END -> 0.0
                TweenMode.STAIR_2 -> (px*2.0).toInt() / 2.0
                TweenMode.STAIR_4 -> (px*4.0).toInt() / 4.0
                TweenMode.STAIR_8 -> (px*8.0).toInt() / 8.0
                TweenMode.STAIR_16 -> (px*16.0).toInt() / 16.0
                TweenMode.STAIR_32 -> (px*32.0).toInt() / 32.0
                TweenMode.EASE_IN_OUT -> smoothstep(0.0, 1.0, px)
                TweenMode.EASE_IN -> if (px < 0.5) smoothstep(0.0, 1.0, px) else px
                TweenMode.EASE_OUT -> if (px > 0.5) smoothstep(0.0, 1.0, px) else px
                TweenMode.SMOOTH_STAIR_8 -> smoothstep(0.5,1.0,(px*8.0)%1.0) * 1.0/8.0 + (px*8.0).toInt() / 8.0
                TweenMode.SMOOTH_STAIR_16 -> smoothstep(0.5,1.0,(px*16.0)%1.0) * 1.0/16.0 + (px*16.0).toInt() / 16.0
                TweenMode.SMOOTH_STAIR_32 -> smoothstep(0.5,1.0,(px*32.0)%1.0) * 1.0/32.0 + (px*32.0).toInt() / 32.0

                TweenMode.EXPONENTIAL_IN -> expoIn(px)
                TweenMode.EXPONENTIAL_OUT -> expoOut(px)
                TweenMode.EXPO_STAIR_4 -> expoIn( expoIn((px*4.0)%1.0) * 1.0/4.0 + (px*4.0).toInt() / 4.0)
                TweenMode.EXPO_STAIR_8 -> expoIn( expoIn((px*8.0)%1.0) * 1.0/8.0 + (px*8.0).toInt() / 8.0)
                TweenMode.EXPO_STAIR_16 -> expoIn( expoIn((px*16.0)%1.0) * 1.0/16.0 + (px*16.0).toInt() / 16.0)


            }

            //val x = smoothstep(0.0, 1.0, px)
            return CameraKey(
                    left.position + (right.position-left.position) * x,
                    slerp(left.orientation, right.orientation, x),
                    left.fov + (right.fov - left.fov) * x,
                    left.aperture + (right.aperture - left.aperture) * x,
                    left.focalPlane + (right.focalPlane - left.focalPlane) * x,
                    left.exposure + (right.exposure - left.exposure) * x,
                    left.centerX + (right.centerX - left.centerX) * x,
                    left.centerY + (right.centerY - left.centerY) * x,
                    time,
                    right.tweenMode)


        }
        if (rightIndex < 0) {
            return keys[keys.size-1]
        }

        return CameraKey(Vector3.ZERO, Quaternion.IDENTITY, 90.0, 1.0, 100.0, 1.0, 0.0, 0.0,-1.0, TweenMode.LINEAR)
    }

}