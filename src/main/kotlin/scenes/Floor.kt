package scenes

import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import rndr.studio.demo.shading.shadowOrthoFunction

class Floor(width: Double = 1500.0, depth: Double = 1500.0) {
    val texture = ColorBuffer.fromUrl("file:data/textures/ground.png")
    val normalMap = ColorBuffer.fromUrl("file:data/textures/ground_normal.png")
    var previousModelView = Matrix44.IDENTITY
    val floor = vertexBuffer(vertexFormat {
        position(3)
        normal(3)
        textureCoordinate(2)
    }, 6)

    init {
        floor.put {
            val p00 = Vector3(-width / 2.0, 0.0, -depth / 2.0)
            val p10 = Vector3(width / 2.0, 0.0, -depth / 2.0)
            val p11 = Vector3(width / 2.0, 0.0, depth / 2.0)
            val p01 = Vector3(-width / 2.0, 0.0, depth / 2.0)

            val t00 = Vector2(0.0, 0.0)
            val t10 = Vector2(1.0, 0.0)
            val t11 = Vector2(1.0, 1.0)
            val t01 = Vector2(0.0, 1.0)

            val n = Vector3.UNIT_Y
            write(p00); write(n); write(t00);
            write(p10); write(n); write(t10);
            write(p11); write(n); write(t11);

            write(p11); write(n); write(t11);
            write(p01); write(n); write(t01);
            write(p00); write(n); write(t00);
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

                    $shadowOrthoFunction
                """

                vertexTransform = """
                    previousView = (p_previousModelView * vec4(x_position,1.0));
                    previousClip = u_projectionMatrix * previousView;
                    currentClip = u_projectionMatrix * u_viewMatrix * u_modelMatrix * vec4(x_position, 1.0);
                """

                fragmentTransform = """
                    vec2 uv = va_position.xz*0.1;
                    float normalFactor = max(1.0 + v_viewPosition.z/100.0, 0.0);

                    vec3 normal = normalize(
                                        mix( vec3(0.0, 1.0, 0.0), texture(p_normalMap, uv).xzy - vec3(0.5, 0.0, 0.5), 1.0-normalFactor)
                                        );
                    vec3 color = pow(texture(p_textureMap, uv).rgb, vec3(2.2))*0.05;

                    mat3 tbn = mat3(u_viewNormalMatrix * u_modelNormalMatrix);

                    //x_fill.rgb = color;
                    o_position.xyz = v_viewPosition.xyz;
                    o_position.w = 0.01;
                    o_normal.xyz = tbn * normal;
                    o_velocity.xy = vec2(0.0, 0.0);
                    o_normal.w = max(1.0 + v_viewPosition.z/100.0, 0.0);

                    o_velocity.xy = (currentClip/currentClip.w - previousClip/previousClip.w).xy*vec2(1280, 720)*0.08;

                    float shadow = 1.0;
                    ${
                if (renderStyle.lights.size > 0) """
                    shadow = shadowOrtho(p_lightMap, v_worldPosition, v_worldNormal, p_lightProj, p_lightView);
                    x_fill.rgb *= (0.5 + 0.5 * shadow); """ else ""


                }
                    //x_fill.rgb *= vec3(0.1, 0.1, 0.1); //o_normal.y*max(1.0 + v_viewPosition.z/100.0, 0.0);
                """
                normalMap.wrapU = WrapMode.REPEAT
                normalMap.wrapV = WrapMode.REPEAT
                texture.wrapU = WrapMode.REPEAT
                texture.wrapV = WrapMode.REPEAT
                parameter("normalMap", normalMap)
                parameter("textureMap", texture)
                output("position", gbuffer.colorBufferIndex("position"))
                output("normal", gbuffer.colorBufferIndex("normal"))
                output("velocity", gbuffer.colorBufferIndex("velocity"))
                parameter("previousModelView", previousModelView)
                if (renderStyle.lights.size > 0) {
                    parameter("lightMap", renderStyle.lights[0].map)
                    parameter("lightProj", renderStyle.lights[0].projection)
                    parameter("lightView", renderStyle.lights[0].view)
                }
            }
            drawer.vertexBuffer(floor, DrawPrimitive.TRIANGLES)
            previousModelView = drawer.view * drawer.model
        }
    }
}