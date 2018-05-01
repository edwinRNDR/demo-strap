package scenes

import modeling.extrudeContour
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.normalMatrix
import org.openrndr.math.transforms.translate
import org.openrndr.shape.Rectangle

class BackBox(size:Double = 1400.0) {

    val cube = vertexBuffer(vertexFormat {
        position(3)
        normal(3)
        attribute("id", 1, VertexElementType.FLOAT32)
        attribute("center", 3, VertexElementType.FLOAT32)
    }, 40)



    init {
        cube.put {
            extrudeContour(Rectangle(Vector2(-size/2.0, -size/2.0), size, size).contour, size, 0.0, this, false, true, -size/2.0)
        }

    }

    fun draw(drawer: Drawer) {

        val gbuffer = RenderTarget.active

        drawer.isolated {
            drawer.view = normalMatrix(drawer.view)
            drawer.model = normalMatrix(drawer.model)
            drawer.shadeStyle = shadeStyle {

                fragmentTransform = """
                    o_position.xyz = v_viewPosition.xyz;
                    o_position.w = -1.0;
                    o_normal.xyz = v_viewNormal.xyz;
                    o_velocity.xy = vec2(0.0, 0.0);
                    o_normal.w = 0.0;
                """
                output("position", gbuffer.colorBufferIndex("position"))
                output("normal", gbuffer.colorBufferIndex("normal"))
                output("velocity", gbuffer.colorBufferIndex("velocity"))
            }
            drawer.vertexBuffer(listOf(cube),  DrawPrimitive.TRIANGLES)
        }
    }

}