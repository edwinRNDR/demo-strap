package sketches

import org.openrndr.Program
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.configuration
import org.openrndr.draw.DepthTestPass
import org.openrndr.math.Vector3
import scenes.Crowd
import scenes.Floor

class Sketch001 : Program() {
    lateinit var crowd: Crowd
    lateinit var floor: Floor
    override fun setup() {
        try {
            crowd = Crowd()
            floor = Floor()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun draw() {
        drawer.depthWrite = true
        drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
        drawer.background(ColorRGBa.PINK)

        drawer.perspective(90.0, 1280.0 / 720.0, 0.1, 1400.0)


        drawer.lookAt(Vector3(0.0, mouse.position.y, -10.0), Vector3(0.0, 0.0, 0.0))

        drawer.fill = ColorRGBa.BLACK
        drawer.fill = ColorRGBa.WHITE
        floor.draw(drawer)
        //crowd.draw(drawer)
    }
}

fun main(args: Array<String>) {
    application(Sketch001(), configuration {
        width = 1280
        height = 720
    })
}