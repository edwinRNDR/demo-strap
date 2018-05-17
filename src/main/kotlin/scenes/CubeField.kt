package scenes

import modeling.extrudeContour
import org.openrndr.draw.*
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.transform
import org.openrndr.math.transforms.translate
import org.openrndr.shape.Rectangle

class CubeField(width: Double = 50.0, depth: Double = 50.0) {

    val cube = vertexBuffer(vertexFormat {
        position(3)
        normal(3)
        attribute("id", 1, VertexElementType.FLOAT32)
        attribute("center", 3, VertexElementType.FLOAT32)
    }, 40)


    val transforms = vertexBuffer(vertexFormat {
        attribute("transform", 16, VertexElementType.FLOAT32)
    }, 20 * 20)

    init {
        cube.put {
            extrudeContour(Rectangle(Vector2(-0.4, 0.0), 0.8, 1.0).contour, 0.2, 0.0, this, true, true, -0.1)
        }

        transforms.put {
            for (y in 0 until 20) {
                for (x in 0 until 20) {
                    write(transform {
                        translate(Vector3(x - 10.0, Math.cos(x * 1.0 + y * 1.0) * 0.5 + 0.5, y - 10.0) * 10.0)

                        scale(5.0)
                        rotate(Vector3.UNIT_Z, Math.random() * 360.0)

                    })
                }
            }
        }
    }

    fun draw(drawer: Drawer) {

        val gbuffer = RenderTarget.active

        drawer.isolated {
            drawer.depthWrite = true
            drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL

            drawer.shadeStyle = shadeStyle {
                vertexTransform = """
                    x_position = (i_transform * vec4(a_position, 1.0)).xyz;
                    """

                fragmentTransform = """
                    x_fill.rgb = vec3(0.2+va_position.y*0.1, 0.0, 0.0);
                    //x_fill.rgb += v_worldNormal.y;
                    //x_fill.rgb = vec3(1.0, 1.0, 1.0);
                    o_position.xyz = v_viewPosition.xyz;
                    o_position.w = 0.1;
                    o_normal.xyz = v_viewNormal.xyz;
                    o_normal.w = 1.0;
                    o_velocity.xy = vec2(0.0, 0.0);
                """
                output("position", gbuffer.colorBufferIndex("position"))
                output("normal", gbuffer.colorBufferIndex("normal"))
                output("velocity", gbuffer.colorBufferIndex("velocity"))

            }
            drawer.vertexBufferInstances(listOf(cube), listOf(transforms), DrawPrimitive.TRIANGLES, 400)
        }
    }

}