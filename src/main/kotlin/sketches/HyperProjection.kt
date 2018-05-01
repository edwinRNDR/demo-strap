package sketches.hyperprojection

import modeling.extrudeContour
import org.openrndr.Program
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.configuration
import org.openrndr.draw.*
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.perspective
import org.openrndr.math.transforms.transform
import org.openrndr.shape.Rectangle

const val steps = 20

class HyperProjection : Program() {

    lateinit var cube: VertexBuffer
    lateinit var rt: RenderTarget
    val format = vertexFormat {
        position(3)
        normal(3)
        attribute("id", 1, VertexElementType.FLOAT32)
        attribute("center", 3, VertexElementType.FLOAT32)
    }


    override fun setup() {

        cube = vertexBuffer(format, 100)
        extrudeContour(Rectangle(Vector2(-0.1, 0.0), 0.2, -1.0).contour, 0.2, 0.0, cube.shadow.writer(), false, true, -0.1)
        cube.shadow.writer().rewind()
        cube.shadow.upload()


        rt = renderTarget(width, height) {
            colorBuffer()
            depthBuffer()
        }
    }

    override fun draw() {

        drawer.fill = ColorRGBa.PINK
        for (i in 0 until steps) {

            drawer.isolatedWithTarget(rt) {

                drawer.background(ColorRGBa.BLACK)
                drawer.depthWrite = true
                drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
//                drawer.perspective(10.0, width * 1.0 / (height * 0.1), 0.1, 100.0)
                //drawer.projection = perspective(( i * (1.0 / steps)) * 0.0 + 30.0, rt.width * 1.0 / rt.height * 1.0, Math.pow(10.0,i-1.0),Math.pow(10.0, i+1.0), 0.0, 0.0)

                drawer.projection = perspective((i+1.0)*90.0/steps , width*1.0/height, 0.1, 100.0, 0.0, 0.0)
                //drawer.rotate(Vector3.UNIT_X, 90.0/(steps-1.0) * i )

                val d = 1.0
                drawer.view = transform {

                    rotate(Vector3.UNIT_X, 90.0/(steps-1.0) * i)
                    translate(Vector3(0.0, -d*i, -15.0 + i * d*1.0))
                }

                for (z in -25..25) {
                    for (x in -5..5) {
                        drawer.isolated {
                            drawer.translate(x * 1.0, 0.0, z * 1.0)
                            drawer.vertexBuffer(cube, DrawPrimitive.TRIANGLES)
                        }
                    }
                }
            }
            drawer.image(rt.colorBuffer(0),

                    Rectangle(0.0, height/2.0 - 0.5 * height/steps, 1.0*width, 1.0*height/ steps),
                    Rectangle(0.0, (steps-1-i) * 1.0*height/steps, 1.0*width, 1.0*height/ steps)

            )
        }
    }


}

fun main(args: Array<String>) {

    application(
            HyperProjection(),
            configuration {
                width = 1920
                height = 1080
            }
    )
}