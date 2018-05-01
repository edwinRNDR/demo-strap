package sketches

import modeling.extrudeContour
import org.openrndr.Configuration
import org.openrndr.Program
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.shape.Rectangle

class Walk001 : Program() {

    lateinit var body: VertexBuffer
    lateinit var leg: VertexBuffer

    override fun setup() {


        val format = vertexFormat {
            position(3)
            normal(3)
            attribute("id", 1, VertexElementType.FLOAT32)
            attribute("center", 3, VertexElementType.FLOAT32)
        }

        body = vertexBuffer(format,100)
        leg = vertexBuffer(format, 100)

        body.shadow.writer().rewind()
        extrudeContour(Rectangle(Vector2(-1.0, -1.0), 2.0, 2.0).contour, 0.3, 0.0, body.shadow.writer(), false, true)
        body.shadow.writer().rewind()



        body.shadow.upload()

        extrudeContour(Rectangle(Vector2(-1.0, -0.05), 2.0, 0.1).contour, 0.3, 0.0, leg.shadow.writer(), false, true)
        leg.shadow.upload()

    }


    override fun draw() {
        drawer.background(ColorRGBa.BLACK)


        drawer.perspective(90.0, width.toDouble()/height.toDouble(), 0.01, 100.0)
        drawer.lookAt(Vector3(0.0, 8.0, 8.0), Vector3.ZERO)

        drawer.fill = ColorRGBa.PINK

        drawer.translate(0.0, 0.0, -seconds)

        drawer.isolated {
            drawer.rotate(Vector3.UNIT_X, 90.0)
            drawer.vertexBuffer(body, DrawPrimitive.TRIANGLES)
        }



        for (i in 0..3) {
            drawer.isolated {
                translate(Vector3(1.0, 0.0, (i-2.0))*0.7)
                rotate(Vector3.UNIT_Z, -30.0)
                rotate(Vector3.UNIT_Y, Math.cos(seconds*Math.PI+i+Math.PI/2)*30.0)
                translate(1.0, 0.0, 0.0)

                vertexBuffer(leg, DrawPrimitive.TRIANGLES)
            }
        }

        for (i in 0..3) {
            drawer.isolated {
                translate(Vector3(-1.0, 0.0, (i-2.0)*0.7 ))
                rotate(Vector3.UNIT_Z, 30.0)
                rotate(Vector3.UNIT_Y, -Math.cos(seconds*Math.PI+i*Math.PI/2+Math.PI/4)*30.0)
                translate(-1.0, 0.0, 0.0)

                vertexBuffer(leg, DrawPrimitive.TRIANGLES)
            }
        }


    }

}

fun main(args: Array<String>) {
    application(Walk001(), Configuration())
}