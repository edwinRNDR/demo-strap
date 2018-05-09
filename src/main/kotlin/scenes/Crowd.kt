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

    fun drawShadow(drawer: Drawer) {

        drawer.isolated {

            drawer.shadeStyle = shadeStyle {
                fragmentTransform = """
                    x_fill.rgb = v_viewPosition;
                    x_fill.a = 1.0;
                """
            }
            for (person in persons)
                vertexBuffer(person, DrawPrimitive.TRIANGLES)

        }
    }

    fun draw(drawer: Drawer, lightMap:ColorBuffer, lightProj:Matrix44, lightView:Matrix44) {

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


                   vec4 lightClip = p_lightProj * p_lightView * vec4(v_worldPosition, 1.0);
                    vec3 lightPersp = (lightClip.xyz / lightClip.w) * 0.5 + 0.5;

                    vec4 lightRef  = p_lightView * vec4(v_worldPosition, 1.0);
                    vec4 positionOfLight = inverse(p_lightView)* vec4(0.0,0.0,0.0,1.0);


                    vec3 d = normalize(positionOfLight.xyz);// - v_worldPosition);
                    float dd = max(0.0, dot(d, normalize(v_worldNormal)));
                    float bias = max(0.5* (1.0 - dd), 0.1);




                    vec2 step = 1.0 / textureSize(p_lightMap, 0 );

                    float sum = 0;
                    for (int j = -2; j <=2 ;++j) {
                    for (int i = -2; i <=2; ++i) {
                        vec3 lightPos = texture(p_lightMap, lightPersp.xy + vec2(i,j)*step).rgb;
                            if (lightPos.z < 0 && lightPos.z-bias > lightRef.z) {
                                sum += 1.0;
                             }
                        }
                    }
                    x_fill.rgb *= vec3( (1.0 - 0.5 * sum/25.0) );

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
                parameter("lightView", lightView)
                parameter("lightProj", lightProj)
                parameter("lightMap", lightMap)

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