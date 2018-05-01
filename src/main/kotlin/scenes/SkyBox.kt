package scenes

import org.openrndr.draw.*
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.normalMatrix

class SkyBox(size:Double = 1400.0) {

    val cube : VertexBuffer
    val cubemap = Cubemap.fromUrl("file:data/textures/evening_irr_hdr32.dds")

    init {
        cube = vertexBuffer(
                vertexFormat {
                    position(3)
                    normal(3)
                    attribute("tangent", 3, VertexElementType.FLOAT32)
                    attribute("binormal", 3, VertexElementType.FLOAT32)
                    textureCoordinate(3)
                }, 6* 3 * 2
        )
        cube.put {
            val p000 = Vector3(-1.0, -1.0, -1.0)
            val p001 = Vector3(-1.0, -1.0, 1.0)
            val p010 = Vector3(-1.0, 1.0, -1.0)
            val p011 = Vector3(-1.0, 1.0, 1.0)

            val p100 = Vector3(1.0, -1.0, -1.0)
            val p101 = Vector3(1.0, -1.0, 1.0)
            val p110 = Vector3(1.0, 1.0, -1.0)
            val p111 = Vector3(1.0, 1.0, 1.0)

            val npx = Vector3(1.0, 0.0, 0.0)
            val nnx = Vector3(-1.0, 0.0, 0.0)
            val npy = Vector3(0.0, 1.0, 0.0)
            val nny = Vector3(0.0, -1.0, 0.0)
            val npz = Vector3(0.0, 0.0, 1.0)
            val nnz = Vector3(0.0, 0.0, -1.0)

            val s =700.0

            // -- positive x
            write(p100*s); write(npx); write(npy); write(npz); write(p100)
            write(p101*s); write(npx); write(npy); write(npz);write(p101)
            write(p111*s); write(npx); write(npy); write(npz);write(p111)

            write(p111*s); write(npx); write(npy); write(npz);write(p111)
            write(p110*s); write(npx); write(npy); write(npz);write(p110)
            write(p100*s); write(npx); write(npy); write(npz);write(p100)

            // -- negative x
            write(p000*s); write(nnx); write(nny); write(nnz);write(p000)
            write(p001*s); write(nnx); write(nny); write(nnz);write(p001)
            write(p011*s); write(nnx); write(nny); write(nnz);write(p011)

            write(p011*s); write(nnx); write(nny); write(nnz); write(p011)
            write(p010*s); write(nnx); write(nny); write(nnz); write(p010)
            write(p000*s); write(nnx); write(nny); write(nnz); write(p000)

            // -- positive y
            write(p010*s); write(npy); write(npx); write(npz); write(p010)
            write(p011*s); write(npy); write(npx); write(npz); write(p011)
            write(p111*s); write(npy); write(npx); write(npz); write(p111)

            write(p111*s); write(npy); write(npx); write(npz); write(p111)
            write(p110*s); write(npy); write(npx); write(npz); write(p110)
            write(p010*s); write(npy); write(npx); write(npz); write(p010)

            // -- negative y
            write(p000*s); write(nny); write(nnx); write(nnz); write(p000)
            write(p001*s); write(nny); write(nnx); write(nnz); write(p001)
            write(p101*s); write(nny); write(nnx); write(nnz); write(p101)

            write(p101*s); write(nny); write(nnx); write(nnz); write(p101)
            write(p100*s); write(nny); write(nnx); write(nnz); write(p100)
            write(p000*s); write(nny); write(nnx); write(nnz); write(p000)

            // -- positive z
            write(p001*s); write(npz); write(npx); write(npy); write(p001)
            write(p011*s); write(npz); write(npx); write(npy); write(p011)
            write(p111*s); write(npz); write(npx); write(npy); write(p111)

            write(p111*s); write(npz); write(npx); write(npy); write(p111)
            write(p101*s); write(npz); write(npx); write(npy); write(p101)
            write(p001*s); write(npz); write(npx); write(npy); write(p001)

            // -- negative z
            write(p000*s); write(nnz); write(nnx); write(nny); write(p000)
            write(p010*s); write(nnz); write(nnx); write(nny); write(p010)
            write(p110*s); write(nnz); write(nnx); write(nny); write(p110)

            write(p110*s); write(nnz); write(nnx); write(nny); write(p110)
            write(p100*s); write(nnz); write(nnx); write(nny); write(p100)
            write(p000*s); write(nnz); write(nnx); write(nny); write(p000)
        }


    }

    fun draw(drawer: Drawer, renderStyle: RenderStyle = RenderStyle()) {

        val gbuffer = RenderTarget.active

        drawer.isolated {
            drawer.view = normalMatrix(drawer.view)
            drawer.model = normalMatrix(drawer.model)

            drawer.shadeStyle = shadeStyle {

                fragmentTransform = """
                    x_fill.rgb =(texture(p_cubemap, va_texCoord0).rgb) * p_skyIntensity;
                    o_position.xyz = v_viewPosition.xyz;
                    o_position.w = -1.0;
                    o_normal.xyz = v_viewNormal.xyz;
                    o_velocity.xy = vec2(0.0, 0.0);
                    o_normal.w = 0.0;
                """
                parameter("cubemap", cubemap)
                parameter("skyIntensity", renderStyle.skyIntensity)

                output("position", gbuffer.colorBufferIndex("position"))
                output("normal", gbuffer.colorBufferIndex("normal"))
                output("velocity", gbuffer.colorBufferIndex("velocity"))
            }
            drawer.vertexBuffer(listOf(cube),  DrawPrimitive.TRIANGLES)
        }
    }

}