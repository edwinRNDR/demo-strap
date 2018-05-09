package scenes

import modeling.bounds
import modeling.loadOBJ
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.transform
import rndr.studio.demo.shading.shadowOrthoFunction
import java.net.URL

class CrowdIntro {
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

    fun drawShadow(drawer:Drawer, time:Double) {
        drawer.isolated {
            drawer.cullTestPass = CullTestPass.BACK
            drawer.shadeStyle = shadeStyle {
                fragmentTransform = "x_fill.rgb = v_viewPosition.xyz;"
            }
            for (person in persons) {
                vertexBuffer(person, DrawPrimitive.TRIANGLES)
                drawer.translate(10.0, 0.0, 0.0)
            }

        }
    }

    fun draw(drawer: Drawer, time:Double = 0.0, renderStyle: RenderStyle) {
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
                    $shadowOrthoFunction
                """

                vertexTransform = """
                    previousView = (p_previousModelView * u_modelMatrix * vec4(x_position,1.0));
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


                    ${if (renderStyle.lights.size > 0)
                        """
                        float shadow = shadowOrtho(p_lightMap, v_worldPosition, v_worldNormal, p_lightProj, p_lightView);
                        x_fill.rgb *= (0.5 + 0.5 * shadow);
                        """
                    else ""}
                    o_velocity.xy = (currentClip/currentClip.w - previousClip/previousClip.w).xy*vec2(1280, 720)*0.08;
                    """
                output("position", gbuffer.colorBufferIndex("position"))
                output("normal", gbuffer.colorBufferIndex("normal"))
                output("velocity", gbuffer.colorBufferIndex("velocity"))
                parameter("time", time)
                parameter("cut", Math.cos(time)*1.0+1.0)
                parameter("irradiance", irradiance)
                parameter("previousModelView", previousModelView)
                if (renderStyle.lights.size > 0) {
                    parameter("lightMap", renderStyle.lights[0].map)
                    parameter("lightProj", renderStyle.lights[0].projection)
                    parameter("lightView", renderStyle.lights[0].view)
                }

            }

            var m = Matrix44.IDENTITY
            for (person in persons) {
                vertexBuffer(person, DrawPrimitive.TRIANGLES)
                drawer.translate(10.0, 0.0, 0.0)
            }

            previousModelView = drawer.view
        }
    }

}