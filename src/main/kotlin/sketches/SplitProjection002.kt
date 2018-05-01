package sketches.splitprojection02

import modeling.extrudeContour
import org.openrndr.Program
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.configuration
import org.openrndr.draw.*
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.perspective
import org.openrndr.math.transforms.perspectiveHorizontal
import org.openrndr.math.transforms.transform
import org.openrndr.shape.Rectangle


class SplitProjection002 : Program() {

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


        rt = renderTarget(width/4, height) {
            colorBuffer()
            depthBuffer()
        }
    }

    override fun draw() {

        drawer.fill = ColorRGBa.PINK
        for (i in 0 until 4) {

            drawer.isolatedWithTarget(rt) {

                drawer.background(ColorRGBa.BLACK)
                drawer.depthWrite = true
                drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
//                drawer.perspective(10.0, width * 1.0 / (height * 0.1), 0.1, 100.0)
                //drawer.projection = perspective(( i * (1.0 / steps)) * 0.0 + 30.0, rt.width * 1.0 / rt.height * 1.0, Math.pow(10.0,i-1.0),Math.pow(10.0, i+1.0), 0.0, 0.0)

                drawer.projection = perspectiveHorizontal(90.0 , width*1.0/height, 0.1, 100.0, 0.0, 0.0)
                //drawer.rotate(Vector3.UNIT_X, 90.0/(steps-1.0) * i )

                val d = 1.0
                drawer.view = transform {

                    rotate(Vector3.UNIT_Y, i*90.0-45.0)
                    translate(Vector3(0.0, -2.0, -10.0))
                }


                rotate(Vector3.UNIT_Y, seconds*10.0)
                rotate(Vector3.UNIT_Z, seconds*10.0)
                drawer.shadeStyle = shadeStyle {
                    fragmentTransform = """
                        x_fill.rgb *= (v_viewNormal.y*0.5 + 0.5) * (cos(va_position.y*30.0)*0.5+0.5);
                    """

                }

                drawer.translate(Math.cos(seconds)*4.0, 0.0, 0.0)

                for (z   in 0..25) {
                    for (x in -5..5) {
                        drawer.isolated {
                            drawer.translate(x * 1.0, 0.0, z * 1.0)
                            drawer.vertexBuffer(cube, DrawPrimitive.TRIANGLES)
                        }
                    }
                }
            }

            drawer.image(rt.colorBuffer(0), rt.width*i*1.0, 0.0)


        }
    }


}

fun main(args: Array<String>) {

    application(
            SplitProjection002(),
            configuration {
                width = 1920
                height = 1080
            }
    )
}