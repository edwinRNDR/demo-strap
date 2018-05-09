package scenes

import modeling.bounds
import modeling.loadOBJ
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.transform
import java.net.URL

class Crowd02 {

    val persons = mutableListOf<VertexBuffer>()
    val irradiance = Cubemap.fromUrl("file:data/textures/evening_irr_hdr32.dds")

    var previousModelView = Matrix44.IDENTITY

    init {
        val meshes = loadOBJ(URL("file:data/meshes/crowd.obj"))
        val format = vertexFormat {
            position(3)
            normal(3)
        }

        for (mesh in meshes.values) {

            val bounds = bounds(mesh)
            println(bounds.corner)
            val correct = transform {
                translate(bounds.corner * -1.0)
            }

            val person = vertexBuffer(format, mesh.size * 3)
            person.put {
                mesh.forEach {

                    val corrected = it.transform(correct)

                    for (i in 0 until 3) {
                        write(corrected.positions[i] / 50.0)
                        write(corrected.normals[i])
                    }
                }
            }
            persons.add(person)
        }
    }

    var positions = (0..1000).map { Vector3.ZERO }.toMutableList()
    var directions = (0..1000).map { Vector3.ZERO }.toMutableList()
    var lastUpdate = (0..1000).map { 0L }.toMutableList()
    var durations = (0..1000).map { Math.random() * 500 + 500 }.toMutableList()

    fun update() {

        for (i in 0 until lastUpdate.size) {

            if (System.currentTimeMillis() - lastUpdate[i] > durations[i]) {
                positions[i] =
                        Vector3((Math.random() - 0.5) * 40.0, 0.0, (Math.random() - 0.5) * 40.0)

                directions[i] =
                        Vector3((Math.random() - 0.5) * 0.1, 0.0, (Math.random() - 0.5) * 0.1)

                lastUpdate[i] = System.currentTimeMillis()
            }
        }


    }

    fun drawShadow(drawer: Drawer, renderStyle: RenderStyle = RenderStyle()) {

        drawer.isolated {
            drawer.shadeStyle = shadeStyle {
                fragmentTransform = """
                    x_fill.rgb = v_viewPosition;
                    x_fill.r = 1.0;
                    """
            }
            var index = 0
            for (person in persons) {
                val m = (System.currentTimeMillis() - lastUpdate[index]) / 200.0
                drawer.isolated {
                    drawer.translate(positions[index])
                    drawer.shadeStyle?.parameter("previousModelView", drawer.view * drawer.model * transform {
                        //translate(directions[index])
                        index++
                    })

                    vertexBuffer(person, DrawPrimitive.TRIANGLES, 0, (person.vertexCount * m).toInt())
                }
            }
        }
    }

    fun draw(drawer: Drawer, renderStyle: RenderStyle = RenderStyle()) {
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
                    x_fill.rgb *= pow(texture(p_irradiance, normalize(v_worldNormal)).rgb, vec3(1.0));

                    o_normal.xyz = v_viewNormal;
                    o_normal.w = 1.0;
                    o_position.xyz = v_viewPosition;
                    o_position.w = 1.0;

                    vec3 viewDirection = normalize(inverse(mat3(u_viewNormalMatrix)) * v_viewPosition);
                    vec3 s = reflect(v_worldNormal, viewDirection) * max(0.0, dot(normalize(v_viewNormal), normalize(viewDirection)));
                    x_fill.rgb += texture(p_irradiance, normalize(s)).rgb * max(0.0, s.y);

                    o_normal.xyz = v_viewNormal;
                    o_normal.w = 1.0;
                    o_position.xyz = v_viewPosition;
                    o_position.w = 0.1;
                    o_velocity.xy = (currentClip/currentClip.w - previousClip/previousClip.w).xy*vec2(1280, 720)*0.08;


                    """
                output("position", gbuffer.colorBufferIndex("position"))
                output("normal", gbuffer.colorBufferIndex("normal"))
                output("velocity", gbuffer.colorBufferIndex("velocity"))
                parameter("irradiance", irradiance)
                parameter("previousModelView", previousModelView)

            }
            fill = renderStyle.objectFill

            var index = 0
            for (person in persons) {
                val m = (System.currentTimeMillis() - lastUpdate[index]) / 200.0
                drawer.isolated {
                    drawer.translate(positions[index])
                    drawer.shadeStyle?.parameter("previousModelView", drawer.view * drawer.model * transform {
                        //translate(directions[index])
                        index++
                    })

                    vertexBuffer(person, DrawPrimitive.TRIANGLES, 0, (person.vertexCount * m).toInt())
                }
            }

            previousModelView = drawer.view * drawer.model
        }
    }

}