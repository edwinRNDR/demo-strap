package studio.rndr.screens

import modeling.extrudeContour
import modeling.extrudeContourUV
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contour
import studio.rndr.io.jsonFromLZMA
import java.io.File
import java.util.*
import org.openrndr.shape.Rectangle
import rndr.studio.demo.shading.shadowOrthoFunction
import scenes.RenderStyle


fun loadMapScene(file: File):MapScene {
    return jsonFromLZMA(file, MapScene::class.java).apply {
        buildingContours = buildings.map { ShapeContour.fromPoints(it.map {it * 1.0}, true) }
        roadContours = roads.map { ShapeContour.fromPoints(it.map { it * 1.0}, false)}
    }
}

class MapScene(val buildings : List<List<Vector2>>, val areas:List<List<Vector2>>, val roads: List<List<Vector2>>,    var bounds: Rectangle) {

    var buildingContours = listOf<ShapeContour>()
    var roadContours =  listOf<ShapeContour>()
}


class CityStyle(var fill:ColorRGBa, var upIntensity:Double, var patternIntensity:Double, var explode:Double=0.0, val selected:Double=-5.0, val floorFill:ColorRGBa=fill, val floorUpIntensity: Double=upIntensity)

class City {

    val irradiance = Cubemap.fromUrl("file:data/textures/evening_irr_hdr32.dds")

    var style:CityStyle = CityStyle(ColorRGBa.WHITE.shade(0.25), 1.0, 0.0)

    private val floor: VertexBuffer = vertexBuffer(vertexFormat {
        position(3)
        normal(3)
        attribute("objectId", 1, VertexElementType.FLOAT32)
        attribute("up", 3, VertexElementType.FLOAT32)
    }, 40)



    private val mesh: VertexBuffer = vertexBuffer(vertexFormat {
        position(3)
        normal(3)
        attribute("objectId", 1, VertexElementType.FLOAT32)
        attribute("up", 3, VertexElementType.FLOAT32)
        textureCoordinate(2)
    }, 600_000)
    private val vertexCount: Int

    init {
        val map = loadMapScene(File("data/meshes/map.json.lzma"))



        vertexCount = mesh.put {
            val random = Random(303_808_909)
            map.buildings.mapIndexed { idx, it ->
                extrudeContourUV(ShapeContour.fromPoints(it, true), 5.0 + random.nextDouble() * 50.0, idx + 1.0, this, true)
            }
        }
    }


    var previousViewMatrix = Matrix44.IDENTITY
    fun draw(drawer: Drawer,  time: Double, renderStyle: RenderStyle = RenderStyle()) {

        val gbuffer = RenderTarget.active

        drawer.pushModel()
        drawer.translate(-200.0, 0.0, 200.0)
        drawer.rotate(Vector3.UNIT_X, -90.0)

        drawer.pushStyle()
        drawer.shadeStyle = shadeStyle {
            parameter("time", time)

            vertexPreamble = """
                    out vec4 previousView;
                    out vec4 previousClip;
                    out vec4 currentClip;
                """
            fragmentPreamble = """
                    in vec4 previousView;
                    in vec4 previousClip;
                    in vec4 currentClip;

                    #define HASHSCALE 443.8975
                    vec2 hash22(vec2 p) {
	                    vec3 p3 = fract(vec3(p.xyx) * HASHSCALE);
                        p3 += dot(p3, p3.yzx+19.19);
                        return fract(vec2((p3.x + p3.y)*p3.z, (p3.x+p3.z)*p3.y));
                    }
                    $shadowOrthoFunction
                """

            vertexTransform = """
                    previousView = (p_previousModelView * vec4(x_position,1.0));
                    previousClip = u_projectionMatrix * previousView;
                    currentClip = u_projectionMatrix * u_viewMatrix * u_modelMatrix * vec4(x_position, 1.0);
                """

            fragmentTransform = """
                    o_position.xyz = v_viewPosition;
                    o_position.w = max(-1.0, v_viewPosition.z/400.0);

                    o_normal.xyz = normalize(v_viewNormal + vec3(0.0, 0.1, 0.0) * smoothstep(0.9,1.0, mod(v_worldPosition.y*1.0,1.0))) ;

                    o_normal.w = 1.0 * (1.0-smoothstep(0.9,1.0, mod(v_worldPosition.y*1.0,1.0)));
                    o_velocity.xy = vec2(0.0, 0.0);

                    float soft = smoothstep(0.0, 1.0, mod(v_worldPosition.y, 1.0)) * smoothstep(1.0, 0.0, mod(v_worldPosition.y, 1.0));

                    float divisions = 15.0+ (cos(va_objectId*0.432)*10.0);

                    float useg = floor(va_texCoord0.x*divisions);
                    float vseg = floor(v_worldPosition.y);

                    float o = cos(floor(va_objectId+0.5)*0.7931);
                    vec2 n2 = hash22(vec2(useg+o, vseg));
                    float emissive = (int(v_worldPosition.y+va_objectId  +useg )%2)*5.2 * soft * 0.0;


                    float tr = cos(va_objectId*0.9323);
                    float e = n2.x > (0.990 + tr*0.05 + (1.0-p_lightDensity)*0.05*2 ) ? 1.0 : 0.0;

                    e *= smoothstep(0.0, 0.1, va_texCoord0.x*divisions-useg) * smoothstep(1.0, 0.9, va_texCoord0.x*divisions-useg) * smoothstep(0.0, 0.5, v_worldPosition.y-vseg) *  smoothstep(1.0, 0.9, v_worldPosition.y-vseg);;
                    float brick = 1.0;

                    //x_fill.rgb +=  p_upIntensity *(va_normal.z);
                    //

                    vec3 skyColor = vec3(cos(va_objectId)*0.2+0.8) ;
                    float shadow = 1.0;
                    ${
            if (renderStyle.lights.size > 0) """
                    shadow = shadowOrtho(p_lightMap, v_worldPosition, v_worldNormal, p_lightProj, p_lightView);
                    skyColor.rgb *= (0.5 + 0.5 * shadow); """ else ""
            }

                    x_fill.rgb = pow(texture(p_irradiance, normalize(v_worldNormal)).rgb, vec3(1.0)) * skyColor * 0.2 * (mix(0.0, 0.2, brick)) * (min(v_worldPosition.y/10.0, 1.0)) * (1.0-smoothstep(0.9,1.0, mod(v_worldPosition.y*1.0,1.0)))  + e * vec3(0.4+cos(va_objectId*0.432)*0.05, 0.3+cos(va_objectId*0.123)*0.05, 0.2)*(3.0 + 1.0 * cos(n2.y+va_objectId));
                    o_velocity.xy = (currentClip/currentClip.w - previousClip/previousClip.w).xy*vec2(1280, 720) * 0.08;

                    x_fill.rgb *= 5.0;

                    // -- hazing
                    float haze = min(1.0, -v_viewPosition.z/400.0);
                    vec3 viewDirection = normalize(inverse(mat3(u_viewNormalMatrix)) * v_viewPosition);
                    vec3 hazeColor = texture(p_irradiance, viewDirection).rgb * p_skyIntensity;

                    x_fill.rgb = mix(max(vec3(0.0), x_fill.rgb), hazeColor, haze);

//                    if (cos(v_worldPosition.x * 1 + p_time + va_objectId) > 0.0  && v_worldPosition.y > 30.0)  {
//                    discard;
//                    }

                    """
            output("position", gbuffer.colorBufferIndex("position"))
            output("normal", gbuffer.colorBufferIndex("normal"))
            output("velocity", gbuffer.colorBufferIndex("velocity"))
            parameter("previousViewMatrix", previousViewMatrix)
            parameter("patternIntensity", style.patternIntensity)
            parameter("upIntensity", style.upIntensity)
            parameter("explode", style.explode)
            parameter("selected", style.selected)
            parameter("irradiance", irradiance)
            parameter("previousModelView", previousViewMatrix)
            parameter("skyIntensity", renderStyle.skyIntensity)
            parameter("lightDensity", renderStyle.lightDensity)
            parameter("time", time)
            if (renderStyle.lights.size > 0) {
                parameter("lightMap", renderStyle.lights[0].map)
                parameter("lightProj", renderStyle.lights[0].projection)
                parameter("lightView", renderStyle.lights[0].view)
            }
        }



        drawer.fill = style.fill
        drawer.shadeStyle?.parameter("upIntensity", style.upIntensity)
        drawer.shadeStyle?.parameter("patternIntensity", style.patternIntensity)


        drawer.vertexBuffer(mesh, DrawPrimitive.TRIANGLES, 0, vertexCount)
        previousViewMatrix = drawer.view * drawer.model
        drawer.popStyle()
        drawer.popModel()


    }
}