package studio.rndr.post


import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.filter.antialias.FXAA
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
import post.*

class PostProcessor(val width: Int, val height: Int) {

    var enableHDR = false

    var projection = Matrix44.IDENTITY
    var floorCoc = FloorCoc()

    val lensDirt = ColorBuffer.fromUrl("file:data/textures/lensdirt.png")

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


    private val flareGhost = FlareGhost()
    private val bloomDownscale = BloomDownscale()
    private val bloomUpscale = BloomUpscale()

    val fxaa = FXAA()

    val blur1 = ApproximateGaussianBlur()
    val blur2 = ApproximateGaussianBlur()
    val blur3 = ApproximateGaussianBlur()
    val blur4 = ApproximateGaussianBlur()
    val blur5 = ApproximateGaussianBlur()
    val blur6 = ApproximateGaussianBlur()

    val bloomCombine = BloomCombine()
    private val bloom1 = colorBuffer(width/2, height/2, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val bloom2 = colorBuffer(width/4, height/4, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val bloom3 = colorBuffer(width/8, height/8, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val bloom4 = colorBuffer(width/16, height/16, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val bloom5 = colorBuffer(width/32, height/32, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val bloom6 = colorBuffer(width/64, height/64, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)


    private val flareInput = colorBuffer(width/2, height/2,1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val flareFeatures = colorBuffer(width/8, height/8,1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    private val flareBlurred = colorBuffer(width/8, height/8,1.0, ColorFormat.RGBa, ColorType.FLOAT16)

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

    val flareBlur = ApproximateGaussianBlur().apply {
        sigma = 3.0
        window = 9
    }
    val flareCombine = FlareCombine()

    private val dof = colorBuffer(width, height, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)

    var aperture: Double = 1.0
    var focalPlane: Double = 100.0
    var focalLength: Double = 10.0
    var exposure: Double = 1.0

    var frame = 0
    var applyMove = false
    var moveThreshold = 140.0

    val result2 = colorBuffer(width, height,1.0, ColorFormat.RGBa, ColorType.FLOAT16)
    fun createGBuffer(): RenderTarget {
        return renderTarget(width, height) {
            colorBuffer("albedo", ColorFormat.RGBa, ColorType.FLOAT16)
            colorBuffer("position", ColorFormat.RGBa, ColorType.FLOAT16)
            colorBuffer("normal", ColorFormat.RGBa, ColorType.FLOAT16)
            colorBuffer("velocity", ColorFormat.RGB, ColorType.FLOAT16)
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
        floorCoc.apply(cocBlurred, cocBlurred)
        hexDof.apply(cocBlurred, dof)

        subtractConstant.constant = ColorRGBa.WHITE.shade(0.4)
        subtractConstant.apply(dof, emissive)

        subtractConstant.constant = ColorRGBa.WHITE
        subtractConstant.apply(dof, flareInput)

        flareGhost.apply(flareInput, flareFeatures)

        bloomDownscale.apply(emissive, bloom1); blur1.apply(bloom1, bloom1); blur1.apply(bloom1, bloom1)
        bloomDownscale.apply(bloom1, bloom2); blur2.apply(bloom2, bloom2); blur2.apply(bloom2, bloom2)
        bloomDownscale.apply(bloom2, bloom3); blur3.apply(bloom3, bloom3); blur3.apply(bloom3, bloom3)
        bloomDownscale.apply(bloom3, bloom4); blur4.apply(bloom4, bloom4); blur4.apply(bloom4, bloom4)
        bloomDownscale.apply(bloom4, bloom5); blur5.apply(bloom5, bloom5); blur5.apply(bloom5, bloom5)
        bloomDownscale.apply(bloom5, bloom6); blur6.apply(bloom6, bloom6); blur6.apply(bloom6, bloom6)

        bloom1.filter(MinifyingFilter.LINEAR, MagnifyingFilter.LINEAR)
        bloom2.filter(MinifyingFilter.LINEAR, MagnifyingFilter.LINEAR)
        bloom3.filter(MinifyingFilter.LINEAR, MagnifyingFilter.LINEAR)
        bloom4.filter(MinifyingFilter.LINEAR, MagnifyingFilter.LINEAR)
        bloom5.filter(MinifyingFilter.LINEAR, MagnifyingFilter.LINEAR)
        bloom6.filter(MinifyingFilter.LINEAR, MagnifyingFilter.LINEAR)

        bloomUpscale.shape = 0.25
        bloomUpscale.apply(arrayOf(bloom1, bloom2, bloom3, bloom4, bloom5, bloom6), emissive3)

        bloomCombine.gain = 0.2
        bloomCombine.apply( arrayOf(dof, emissive3), dof)
        //add.apply(arrayOf(emissive3, dof), dof)
        bloomCombine.gain = 1.0
        flareBlur.apply(flareFeatures, flareFeatures)
        flareCombine.time = time
        flareCombine.apply(arrayOf(dof, flareFeatures, lensDirt), dof)

        if (!enableHDR) {
            delinearize.apply(dof, result2)
        } else {
            passthrough.apply(dof, result2)
        }
        colorLookup.apply(result2, result2)
        fxaa.maxSpan = 16.0
        fxaa.directionReduceMinimum = 0.5
        fxaa.directionReduceMultiplier = 0.5
        fxaa.lumaThreshold = 0.5

        fxaa.apply(result2, result)
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