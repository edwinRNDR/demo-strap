package scenes

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle

class DisplayLines {

    val rt = renderTarget(1280, 720) {
        colorBuffer()
        depthBuffer()
    }

    val image = ColorBuffer.fromUrl("file:data/overlays/strap.png")

    fun draw(drawer: Drawer, time: Double) {
        drawer.isolatedWithTarget(rt) {
            drawer.shadeStyle = null
            drawer.ortho(rt)
            drawer.model = Matrix44.IDENTITY
            drawer.view = Matrix44.IDENTITY
            drawer.background(ColorRGBa.BLACK)
            drawer.stroke = ColorRGBa(0.4, 0.05, 0.02).shade(1.0 / 0.4).toSRGB()
            drawer.strokeWeight = 1.0
            drawer.lineSegment(0.0, 0.0, 1280.0, 720.0)

            drawer.pushStyle()
            drawer.drawStyle.colorMatrix = tint(ColorRGBa.WHITE.shade(0.5))
            drawer.image(image, image.bounds, Rectangle(Vector2(1280.0, 0.0), -1280.0, 720.0))
            drawer.popStyle()
            for (i in 0 until 20) {
                drawer.lineSegment(Math.cos(i + time) * 180 + 180, i * 36.0 + 18.0, Math.sin(i + time) * 180 + 1100, i * 36.0 + 18.0)

            }
        }
    }
}