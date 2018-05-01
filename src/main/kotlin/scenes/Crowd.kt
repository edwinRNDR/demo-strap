package scenes

import modeling.loadOBJ
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import java.net.URL

class Crowd {

    val persons = mutableListOf<VertexBuffer>()
    val irradiance = Cubemap.fromUrl("file:data/textures/garage_iem.dds")

    var previousModelView = Matrix44.IDENTITY

    init {
        val meshes = loadOBJ(URL("file:data/meshes/crowd.obj"))
        val format = vertexFormat {
            position(3)
            normal(3)
        }

        for (mesh in meshes.values) {
            val person = vertexBuffer(format, mesh.size * 3)
            person.put {
                mesh.forEach {
                    for (i in 0 until 3) {
                        write(it.positions[i]/50.0)
                        write(it.normals[i])
                    }
                }
            }
            persons.add(person)
        }
    }

    fun draw(drawer: Drawer) {
        val gbuffer = RenderTarget.active

        drawer.isolated {

            drawer.shadeStyle = shadeStyle {

                vertexPreamble = """
                    out vec4 previousView;
                    out vec4 previousClip;
                    out vec4 currentClip;
                """
                fragmentPreamble = """
                    in vec4 previousView;
                    in vec4 previousClip;
                    in vec4 currentClip;
                """

                vertexTransform = """
                    previousView = (p_previousModelView * vec4(x_position,1.0));
                    previousClip = u_projectionMatrix * previousView;
                    currentClip = u_projectionMatrix * u_viewMatrix * u_modelMatrix * vec4(x_position, 1.0);
                """



                fragmentTransform = """
                    x_fill.rgb = pow(texture(p_irradiance, normalize(v_worldNormal)).rgb, vec3(2.2))*0.5;

                    //x_fill.rgb = vec3(0.1, 0.0, 0.0) + vec3( max(0.0, v_worldNormal.y));
                    o_normal.xyz = v_viewNormal;
                    o_normal.w = 1.0;
                    o_position.xyz = v_viewPosition;
                    o_position.w = 0.1;
                    o_velocity.xy = (currentClip/currentClip.w - previousClip/previousClip.w).xy*vec2(1280, 720)*0.08;


                    """
                output("position",gbuffer.colorBufferIndex("position"))
                output("normal", gbuffer.colorBufferIndex("normal"))
                output("velocity",gbuffer.colorBufferIndex("velocity"))
                parameter("irradiance", irradiance)
                parameter("previousModelView", previousModelView)

            }
            fill = ColorRGBa.RED
            for (person in persons)
                vertexBuffer(person, DrawPrimitive.TRIANGLES)

            previousModelView = drawer.view * drawer.model
        }
    }

}