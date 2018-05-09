package scenes

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.math.Matrix44

class RenderStyle {
    var skyIntensity = 0.25
    var velocityScale = 0.08
    var lightDensity = 1.0
    var objectFill = ColorRGBa.WHITE
    var lights = mutableListOf<Light>()
}

class Light {
    var view = Matrix44.IDENTITY
    var projection = Matrix44.IDENTITY
    lateinit var map: ColorBuffer
}