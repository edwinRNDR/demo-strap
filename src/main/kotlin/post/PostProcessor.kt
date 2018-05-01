package studio.rndr.post


import org.openrndr.draw.*
import org.openrndr.filter.blend.add
import org.openrndr.filter.blend.multiply
import org.openrndr.filter.blend.passthrough
import org.openrndr.filter.blend.subtract
import org.openrndr.filter.blur.ApproximateGaussianBlur
import org.openrndr.filter.blur.BoxBlur
import org.openrndr.filter.blur.HashBlur
import org.openrndr.filter.color.ColorLookup
import org.openrndr.filter.color.delinearize
import org.openrndr.filter.screenspace.*
import org.openrndr.filter.unary.SubtractConstant
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.scale
import org.openrndr.math.transforms.translate

class PostProcessor(val width: Int, val height: Int) {

    var enableHDR = false

    var projection = Matrix44.IDENTITY

    class Luts {
        val purple03 = ColorBuffer.fromUrl("file:data/color-lookup/purple-03.png")
        val horrorBlue = ColorBuffer.fromUrl("file:data/color-lookup/horror-blue.png")
        var cripWinter = ColorBuffer.fromUrl("file:data/color-lookup/crisp-winter.png")
        var neutral = ColorBuffer.fromUrl("file:data/color-lookup/neutral.png")
    }
    val luts = Luts()
    val colorLookup = ColorLookup(luts.purple03)

    var lut: ColorBuffer
    set(value) {
        colorLookup.lookup = value
    }
    get() = colorLookup.lookup

    private val ssao = Ssao()
    private val sslr = Sslr()
    private val hexDof = HexDof()
    private val velocityBlur = VelocityBlur()
    private val velocityBlur2 = IterativeVelocityBlur()
    private val positionToCoc = PositionToCoc()
    private var occlusionBlur = OcclusionBlur()
    private val reflection = colorBuffer(width, height, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val occlusion = colorBuffer(width / 2, height / 2, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val occlusion2x = colorBuffer(width, height, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val coc = colorBuffer(width, height, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val cocBlurred = colorBuffer(width, height, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val subtractConstant = SubtractConstant()
    private val emissive = colorBuffer(width / 2, height / 2, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val emissive2 = colorBuffer(width / 4, height / 4, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val emissive3 = colorBuffer(width / 1, height / 1, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)

    private val hashBlur = HashBlur().apply {
        gain = 0.05
    }

    private val emissiveBlur = ApproximateGaussianBlur().apply {
        sigma = 3.0
        window = 9
    }
    private val emissiveBlur2 = ApproximateGaussianBlur().apply {
        sigma = 3.0
        window = 9
    }

    private val move = Move()
    val moveBuffers = Array<ColorBuffer>(2, { colorBuffer(width, height, 1.0, ColorFormat.RGBa, ColorType.FLOAT16) })

    val lapBlur = BoxBlur()
    val lapBlurred0 = colorBuffer(width, height, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    val lapBlurred1 = colorBuffer(width, height, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)

    private val dof = colorBuffer(width, height, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)

    var aperture: Double = 1.0
    var focalPlane: Double = 100.0
    var focalLength: Double = 10.0
    var exposure: Double = 1.0

    var frame = 0
    var applyMove = false
    var moveThreshold = 140.0

    fun createGBuffer(): RenderTarget {
        return renderTarget(width, height) {
            colorBuffer("albedo", ColorFormat.RGBa, ColorType.FLOAT16)
            colorBuffer("position", ColorFormat.RGBa, ColorType.FLOAT16)
            colorBuffer("normal", ColorFormat.RGBa, ColorType.FLOAT16)
            colorBuffer("velocity", ColorFormat.RG, ColorType.FLOAT16)
            depthBuffer()
        }
    }

    fun apply(gbuffer: RenderTarget, result: ColorBuffer,time:Double = 0.0) {
        hashBlur.time = time

        gbuffer.colorBuffer(1).filterMin = MinifyingFilter.NEAREST
        gbuffer.colorBuffer(2).filterMin = MinifyingFilter.NEAREST

        ssao.colors = gbuffer.colorBufferIndex("albedo")
        ssao.positions = gbuffer.colorBufferIndex("position")
        ssao.normals = gbuffer.colorBufferIndex("normal")
        ssao.projection = projection
        ssao.radius = 32.0
        ssao.apply(gbuffer.colorBuffers.toTypedArray(), occlusion)

        occlusionBlur.apply(occlusion, occlusion2x)
        //passthrough.apply(occlusion, occlusion2x)

        multiply.apply(arrayOf(gbuffer.colorBuffer(0), occlusion2x), occlusion2x)

        sslr.colors = gbuffer.colorBufferIndex("albedo")
        sslr.positions = gbuffer.colorBufferIndex("position")
        sslr.normals = gbuffer.colorBufferIndex("normal")
        sslr.projection = scale(gbuffer.width / 2.0, gbuffer.height / 2.0, 1.0) * translate(Vector3(1.0, 1.0, 0.0)) * projection
        sslr.apply(arrayOf(occlusion2x, gbuffer.colorBuffer(1), gbuffer.colorBuffer(2)), reflection)

        add.apply(arrayOf(occlusion2x, reflection), reflection)

        positionToCoc.minCoc = 2.0
        positionToCoc.maxCoc = 20.0 * (gbuffer.width/1280.0)
        positionToCoc.aperture = aperture * (gbuffer.width/1280.0)
        positionToCoc.focalPlane = focalPlane
        positionToCoc.focalLength = focalLength
        positionToCoc.exposure = exposure

        hexDof.samples = (20 * (gbuffer.width/1280.0)).toInt()
        positionToCoc.apply(arrayOf(reflection, gbuffer.colorBuffer("position")), coc)

        velocityBlur2.iterations = 10 + (width/1280)-1
        velocityBlur2.apply(arrayOf(coc, gbuffer.colorBuffer("velocity")), cocBlurred)
        hexDof.apply(cocBlurred, dof)

        subtractConstant.apply(dof, emissive)
        hashBlur.apply(emissive, emissive3)
        emissiveBlur.apply(emissive, emissive)
        emissiveBlur2.gain = 0.5
        emissiveBlur2.apply(emissive, emissive2)


        add.apply(arrayOf(emissive2, emissive3), emissive3)
        add.apply(arrayOf(emissive3, dof), dof)

        if (!enableHDR) {
            delinearize.apply(dof, result)
        } else {
            passthrough.apply(dof, result)
        }
        colorLookup.apply(result, result)
        if (!applyMove) {
            passthrough.apply(result, moveBuffers[frame % 2])
        }
        if (applyMove) {
            lapBlur.window = 1
            lapBlur.apply(result, lapBlurred0)
            lapBlur.apply(lapBlurred0, lapBlurred1)

            subtract.apply(arrayOf(lapBlurred0, lapBlurred1), lapBlurred1)
            subtract.apply(arrayOf(result, lapBlurred0), lapBlurred0)

            move.time = time
            move.threshold = moveThreshold
            move.apply(arrayOf(moveBuffers[(frame + 1) % 2], gbuffer.colorBuffer("velocity"), lapBlurred0, lapBlurred1, result), moveBuffers[frame % 2])
            //add.apply(arrayOf(lapBlurred, moveBuffers[frame%2]), moveBuffers[frame%2])
            passthrough.apply(moveBuffers[frame % 2], result)
        }

        frame++
    }


}