package rndr.studio.demo.camera

import org.openrndr.math.Vector3
import org.openrndr.math.smootherstep
import java.util.*

enum class NoiseType {
    UNIFORM,
    GAUSSIAN,
    BROWNIAN,
}

class NoiseTable(val length: Int = 1024, type: NoiseType, density: Double = 1.0, magnitude:Double=1.0) {


    val table = when (type) {
        NoiseType.UNIFORM -> (0 until length).map {
            if (Math.random() < density) {
                Vector3(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5)
            } else {
                Vector3.ZERO
            }
        }
        NoiseType.GAUSSIAN -> {
            val r = Random()
            (0 until length).map {
                if (Math.random() < density) {
                    Vector3(r.nextGaussian() * 0.5, r.nextGaussian() * 0.5, r.nextGaussian() * 0.5)
                } else {
                    Vector3.ZERO
                }
            }
        }
        NoiseType.BROWNIAN -> {
            val r = Random()
            var last = Vector3.ZERO

            (0 until length).map {  it ->
                if (Math.random() < density) {

                   var next = last * (1.0-magnitude) +
                    Vector3(r.nextGaussian() * 0.5, r.nextGaussian() * 0.5, r.nextGaussian() * 0.5).normalized * r.nextGaussian() * magnitude
                    last = next
                    next
                } else {
                    last
                }
            }


        }
    }

    operator fun get(t: Double): Vector3 {

        val ti0 = t.toInt()
        val ti1 = ti0 + 1

        val t0 = t - ti0
        val s0 = smootherstep(0.0, 1.0, t0)

        val v0 = table[ti0 % length]
        val v1 = table[(ti1 % length)]

        return v0 * (1.0 - s0) + v1 * s0
    }

}