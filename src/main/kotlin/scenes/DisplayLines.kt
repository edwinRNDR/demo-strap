package scenes

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.math.Matrix44

class DisplayLines {

    val rt = renderTarget(1280, 720) {
        colorBuffer()
        depthBuffer()
    }

    fun draw(drawer: Drawer, time:Double) {
        drawer.isolatedWithTarget(rt) {
            drawer.shadeStyle = null
            drawer.ortho(rt)
            drawer.model = Matrix44.IDENTITY
            drawer.view = Matrix44.IDENTITY
            drawer.background(ColorRGBa.BLACK)
            drawer.stroke = ColorRGBa(0.4, 0.05, 0.02).shade(1.0/0.4).toSRGB()
            drawer.strokeWeight = 1.0
            drawer.lineSegment(0.0, 0.0, 1280.0, 720.0)

            for (i in 0 until 20) {
                drawer.lineSegment(Math.cos(i+time)*180+180, i*36.0+18.0, Math.sin(i+time)*180+1100, i*36.0+18.0)

            }
        }
    }
}