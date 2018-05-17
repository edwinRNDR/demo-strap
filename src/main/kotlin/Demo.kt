package studio.rndr.demo

import org.openrndr.*
import org.openrndr.animatable.Animatable
import org.openrndr.animatable.Clock
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
//import org.openrndr.ffmpeg.MP4Profile
//import org.openrndr.ffmpeg.VideoWriter
import org.openrndr.math.*
import org.openrndr.math.transforms.lookAt
import org.openrndr.math.transforms.ortho
import org.openrndr.shape.Rectangle
import org.openrndr.text.Writer
import rndr.studio.demo.camera.NoiseTable
import rndr.studio.demo.camera.NoiseType
import scenes.*
import studio.rndr.camera.CameraKeyframer
import studio.rndr.camera.FirstPersonCamera
import studio.rndr.demo.bass.Channel
import studio.rndr.demo.bass.initBass
import studio.rndr.demo.bass.openStream
import studio.rndr.demosystem.Scheduler
import studio.rndr.post.PostProcessor
import studio.rndr.screens.City
import java.io.File

/*
vec3 aberrationColor(float f)
{
    f = f * 3.0 - 1.5;
    return saturate(vec3(-f, 1.0 - abs(f), f));
}
 */

fun rampInOut(start: Double, end: Double, accel: Double, time: Double): Double {
    val t = time - start
    val d = end - start
    val a = 1.0 / (accel / d)
    val tn = t / d
    return if (tn >= 0 && tn <= 1.0) {
        (a * tn).coerceIn(0.0, 1.0) * (a * (1.0 - tn)).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
}

class FrameClock : Clock {
    private var frame = 0
    override val time: Long
        get() = (frame * (1000.0 / 60.0)).toLong()

    fun next() {
        frame++
    }
}

/**
 * Demo skeleton
 */
class Demo : Program() {
    private lateinit var channel: Channel
    private val scheduler = Scheduler(112.0)

    private var showDebug = false
    val targetWidth = 1280
    val targetHeight = 720

    lateinit var gbuffer: RenderTarget
    lateinit var post: PostProcessor
    private lateinit var result: ColorBuffer

    private lateinit var finalTarget: RenderTarget
    private val subtitles = File("data/texts/subtitles.txt").readLines()

    private var dumpVideo = false

    lateinit var frameClock: FrameClock
//    lateinit var videoWriter: VideoWriter

    override fun setup() {
        if (dumpVideo) {
            frameClock = FrameClock()
            Animatable.clock(frameClock)
//            videoWriter = VideoWriter().size(targetWidth, targetHeight).frameRate(60).profile(MP4Profile().constantRateFactor(20)).start()
        }

        finalTarget = renderTarget(targetWidth, targetHeight) {
            colorBuffer()
        }
        initBass()
        println("loading music")

        if (!dumpVideo)
            channel = openStream("data/audio/demo.mp3")


        println("loaded music")
        keyboard.keyDown.filter { it.key == KEY_ARROW_LEFT }.listen {
            channel.setPosition((channel.getPosition() - (60.0 / scheduler.bpm) * 16).coerceAtLeast(0.0))
        }

        keyboard.keyDown.filter { it.key == KEY_ARROW_RIGHT }.listen {
            channel.setPosition(channel.getPosition() + (60.0 / scheduler.bpm) * 16)
        }

        keyboard.keyDown.filter { it.name == "d" }.listen {
            showDebug = !showDebug
        }


        post = PostProcessor(targetWidth, targetHeight)
        gbuffer = post.createGBuffer()

        result = colorBuffer(targetWidth, targetHeight, 1.0, ColorFormat.RGBa, ColorType.FLOAT16)

        schedule()
        if (!dumpVideo) {
            channel.play()
        }
    }

    /**
     * Build the demo's schedule
     */
    private fun schedule() {
        val font96 = FontImageMap.fromUrl("file:data/fonts/CooperHewitt-Heavy.otf", 96.0)
        val beatDuration = 60.0 / scheduler.bpm
        val barDuration = beatDuration * 4
        val patternDuration = barDuration * 4

        val backBox = BackBox(1400.0)
        val floor = Floor()
        val city = City()
        val skyBox = SkyBox()
        val cityFloor = CityFloor(city.irradiance)

        val lightMap = renderTarget(2048, 2048) {
            colorBuffer(format = ColorFormat.RGB, type = ColorType.FLOAT32)
            depthBuffer()
        }

        fun drawLight(rt: RenderTarget, light: Light, drawFunction: () -> (Unit)) {
            drawer.isolatedWithTarget(rt) {
                drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
                drawer.depthWrite = true
                projection = light.projection
                view = light.view
                drawer.model = Matrix44.IDENTITY
                background(ColorRGBa.BLACK)
                drawFunction()
            }
        }

        fun drawPost(camera: FirstPersonCamera, drawFunction: () -> (Unit)) {
            drawer.isolatedWithTarget(gbuffer) {
                drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
                drawer.depthWrite = true
                projection = org.openrndr.math.transforms.perspective(camera.fov, targetWidth * 1.0 / targetHeight, 0.1, 1400.0, camera.centerX * 0.1, camera.centerY * 0.1)
                post.projection = projection
                post.projection = drawer.projection
                post.aperture = camera.aperture
                post.focalPlane = camera.focalPlane
                post.exposure = camera.exposure

                drawer.view = camera.viewMatrix
                drawer.model = Matrix44.IDENTITY

                background(ColorRGBa.BLACK)
                drawFunction()
            }
        }

        run {
            val cameras = (1..4).map { CameraKeyframer.fromFile("data/tracks/display-${String.format("%02d", it)}.json") }
            val displayLines = DisplayLines()
            val texturedRectangle = TexturedRectangle(displayLines.rt.colorBuffer(0))
            scheduler.task(barDuration * 8) {
                post.lut = post.luts.purple03

                val camera = FirstPersonCamera().fromFrame(cameras[it.bar.toInt() / 2].frame(it.beat % 8.0)
                        .copy(exposure = rampInOut(0.0, 8.0, 1.0, it.beat % 8.0))
                )
                drawPost(camera) {
                    displayLines.draw(drawer, it.beat)
                    displayLines.rt.colorBuffer(0).generateMipmaps()

                    drawer.fill = ColorRGBa.BLACK
                    backBox.draw(drawer)
                    drawer.fill = ColorRGBa.WHITE.shade(0.1)
                    floor.draw(drawer)
                    texturedRectangle.draw(drawer)
                }
                post.apply(gbuffer, result, it.time)
                drawer.image(result)

                val w = Writer(drawer)
                drawer.fontMap = font96
                drawer.fill = ColorRGBa.WHITE.opacify(1.0)

                w.box = Rectangle(Vector2(100.0, 100.0), 1280.0, 720.0)
                w.newLine()

                drawer.isolated {
                    drawer.shadeStyle = shadeStyle {
                        fragmentTransform = """
                        float characterId = va_position.z;
                        vec2 d = va_bounds.xy - vec2(0.5);
                        float dl = length(d) - characterId*0.05;
                        x_fill.a *= smoothstep(1.0, 0.0, dl);
                        """
                    }
                }
            }
            scheduler.complete()
        }

        run {
            val crowd = CrowdIntro()
            val noiseTable = NoiseTable(type = NoiseType.BROWNIAN, density = 0.8)
            for (i in 1 until 5) {
                val ckf = CameraKeyframer.fromFile("data/tracks/person-${String.format("%02d", i)}.json")
                scheduler.task(barDuration * 2) {
                    val frame = ckf.frame(it.beat / 2.0
                    ).let { f ->
                        f.copy(
                                position = f.position + noiseTable[it.time * 0.43] * Vector3(1.0, 1.0, 0.1) * 0.05,
                                orientation = (noiseTable[it.time * 2.29] * 2.0).let { r -> f.orientation * Quaternion.fromAngles(r.x, r.y, r.z * 0.1) },
                                exposure = rampInOut(0.0, 7.0, 1.0, it.beat % 8.0))
                    }

                    post.lut = post.luts.cripWinter

                    val camera = FirstPersonCamera().fromFrame(frame)
                    drawPost(camera) {
                        val renderStyle = RenderStyle()
                        drawer.fill = ColorRGBa.WHITE.shade(0.02)
                        skyBox.draw(drawer)
                        cityFloor.draw(drawer)
                        drawer.fill = ColorRGBa.WHITE.shade(0.1 + 0.7 * rampInOut(2.0, 8.0, 0.25, it.beat) * (noiseTable[it.time * 60.0].x * 0.5 + 0.5)
                                + 0.5 * rampInOut(1.75, 2.75, 0.5, it.beat)
                        )
                        crowd.draw(drawer, it.beat, renderStyle)
                    }
                    post.apply(gbuffer, result, it.time)
                    drawer.image(result)
                }
                scheduler.complete()
            }
        }

        run {
            val crawlers = CrawlersIntro()
            val cameras = (0..1).map { CameraKeyframer.fromFile("data/tracks/crawler-intro-${String.format("%02d", it)}.json") }

            scheduler.task(patternDuration * 2) {
                post.lut = post.luts.neutral

                if (it.beat < 4.0) {
                    post.moveThreshold = 140.0
                    post.applyMove = true
                } else {
                    post.applyMove = false
                }

                val camera = cameras[it.bar.toInt() % 2].frame(it.beat).let { f ->
                    f.copy(exposure = f.exposure * rampInOut(0.1, 4.0, 0.5, it.beat % 4.0))
                }

                drawPost(FirstPersonCamera().fromFrame(camera)) {
                    drawer.fill = ColorRGBa.WHITE.shade(0.01)
                    backBox.draw(drawer)
                    floor.draw(drawer)
                    crawlers.draw(drawer, it.beat)
                }
                post.apply(gbuffer, result, it.time)
                drawer.image(result)
            }
            scheduler.complete()
        }

        run {
            val crawlers = Crawlers()
            val crowd = Crowd02()
            val cameras = (1..2).map { CameraKeyframer.fromFile("data/tracks/crawler-carnage-${String.format("%02d", it)}.json") }

            scheduler.task(patternDuration * 2) {
                post.lut = post.luts.cripWinter

                val renderStyle = RenderStyle()
                val camera = cameras[it.bar.toInt() % 2].frame(it.beat % 28).let { f ->
                    f.copy(exposure = f.exposure * rampInOut(0.1, 4.0, 0.5, it.beat % 4.0))
                }

                val fps = FirstPersonCamera().fromFrame(camera)
                val light = Light().apply {
                    projection = ortho(-10.0, 10.0, -10.0, 10.0, 0.0, 100.0)

                    view = lookAt(fps.position + Vector3(0.0, 10.0, 0.0), camera.position + fps.forward - Vector3(0.0, 1.0, 0.0), Vector3.UNIT_Y)
                    map = lightMap.colorBuffer(0)
                }

                crowd.update(it.scheduleTime)
                drawLight(lightMap, light) {
                    crawlers.drawShadow(drawer, it.beat % 8.0)
                    crowd.drawShadow(drawer, it.scheduleTime)
                }

                drawPost(fps) {
                    renderStyle.objectFill = ColorRGBa.WHITE.shade(1.0 - (it.time / it.duration))
                    renderStyle.lights.add(light)
                    drawer.fill = ColorRGBa.WHITE.shade(0.05 * (1.0 - (it.time / it.duration)))
                    backBox.draw(drawer)
                    floor.draw(drawer, renderStyle)

                    crawlers.draw(drawer, it.beat, update = false)
                    crowd.draw(drawer, it.scheduleTime, renderStyle)
                }

                if (it.beat < 2.0) {
                    post.moveThreshold = smoothstep(0.0, 0.5, 1.0 - (it.beat / 2.0))
                    post.applyMove = true
                } else {
                    post.applyMove = false
                }

                post.apply(gbuffer, result, it.time)
                drawer.image(result)
            }
            scheduler.complete()
        }
        run {
            val megaCrawlers = MegaCrawlers()
            val cameras = (1..4).map { CameraKeyframer.fromFile("data/tracks/city-scan-${String.format("%02d", it)}.json") }
            scheduler.task(patternDuration * 2) {
                post.lut = post.luts.neutral

                val renderStyle = RenderStyle()
                renderStyle.skyIntensity = when (it.beat) {
                    in 0.0..8.0 -> 0.25
                    in 8.0..16.0 -> 0.125
                    else -> 0.0685
                }

                val frame = cameras[(it.bar / 2).toInt() % 4].frame(it.beat % 8.0
                ).copy(exposure = rampInOut(0.0, 8.0, 1.0, it.beat % 8.0))

                drawPost(FirstPersonCamera().fromFrame(frame)) {
                    drawer.fill = ColorRGBa(0.5, 0.7, 1.0).shade(0.05)
                    skyBox.draw(drawer, renderStyle)
                    cityFloor.draw(drawer, renderStyle)
                    city.draw(drawer, it.beat, renderStyle)
                    megaCrawlers.draw(drawer, it.beat)
                }
                post.apply(gbuffer, result, it.time)
                drawer.image(result)
            }
            scheduler.complete()
        }

        run {
            val megaCrawlers = MegaCrawlers()
            val cameras = (1..4).map { CameraKeyframer.fromFile("data/tracks/city-fly-${String.format("%02d", it)}.json") }
            scheduler.task(patternDuration * 2) {

                post.lut = post.luts.neutral
                val light = Light().apply {
                    projection = ortho(-500.0, 500.0, -500.0, 500.0, 0.0, 500.0)
                    view = lookAt(Vector3(0.0, 500.0, -300.0), Vector3(0.0, 0.0, -300.0), Vector3.UNIT_Z)
                    map = lightMap.colorBuffer(0)
                }

                drawLight(lightMap, light) {
                    megaCrawlers.drawShadow(drawer, it.beat % 8.0)
                }

                val frame = cameras[(it.bar / 2).toInt() % 4].frame(it.beat % 8.0)
                        .copy(exposure = rampInOut(0.0, 8.0, 1.0, it.beat % 8.0))

                drawPost(FirstPersonCamera().fromFrame(frame)) {
                    val renderStyle = RenderStyle()
                    renderStyle.lights.add(light)
                    renderStyle.skyIntensity = 0.0685
                    drawer.fill = ColorRGBa(0.5, 0.7, 1.0).shade(0.05)
                    skyBox.draw(drawer, renderStyle)
                    cityFloor.draw(drawer, renderStyle)
                    city.draw(drawer, it.beat, renderStyle)
                    megaCrawlers.draw(drawer, it.beat)
                }
                post.apply(gbuffer, result, it.time)
                drawer.image(result)
                //drawer.image(lightMap.colorBuffer(0))
            }
            scheduler.complete()

        }
        run {
            val megaCrawlers = MegaMarchingCrawlers()
            val cameras = (1..4).map { CameraKeyframer.fromFile("data/tracks/city-march-${String.format("%02d", it)}.json") }
            scheduler.task(patternDuration * 2) {
                val frame = cameras[(it.bar / 2).toInt() % 4].frame(it.beat % 8.0)
                        .copy(exposure = rampInOut(0.0, 8.0, 1.0, it.beat % 8.0))

                post.lut = post.luts.purple03

                val light = Light().apply {
                    projection = ortho(-500.0, 500.0, -500.0, 500.0, 0.0, 500.0)
                    view = lookAt(Vector3(0.0, 500.0, -300.0), Vector3(0.0, 0.0, -300.0), Vector3.UNIT_Z)
                    map = lightMap.colorBuffer(0)
                }

                drawLight(lightMap, light) {
                    megaCrawlers.drawShadow(drawer, it.beat % 8.0)
                }

                drawPost(FirstPersonCamera().fromFrame(frame)) {
                    val renderStyle = RenderStyle()
                    renderStyle.lights.add(light)
                    renderStyle.skyIntensity = if (it.beat % 2.0 < 1.0) 0.0685 * 4.0 else (1.0 - ((it.beat % 2.0) - 1.0)) * 0.4 + 0.0685 * 4.0
                    drawer.fill = ColorRGBa(0.5, 0.7, 1.0).shade(0.05)
                    skyBox.draw(drawer, renderStyle)
                    cityFloor.draw(drawer, renderStyle)
                    city.draw(drawer, it.beat, renderStyle)
                    megaCrawlers.draw(drawer, it.beat % 8.0, renderStyle, false)
                }
                post.applyMove = true
                post.moveThreshold = rampInOut(-0.5, 0.75, 0.5, it.beat % 2.0)

                post.apply(gbuffer, result, it.time)
                drawer.image(result)
            }
            scheduler.complete()

        }
        run {
            val cameras = (4..4).map { CameraKeyframer.fromFile("data/tracks/city-${String.format("%02d", it)}.json") }
            scheduler.task(barDuration * 2) {

                post.lut = post.luts.cripWinter

                val renderStyle = RenderStyle()
                renderStyle.skyIntensity = when (it.beat) {
                    in 0.0..8.0 -> 0.25
                    in 8.0..16.0 -> 0.125
                    else -> 0.0685
                }

                val frame = cameras[(it.bar / 2).toInt() % 4].frame((it.beat / 2.0) % 8.0
                ).copy(exposure = rampInOut(0.0, 8.0, 1.0, (it.beat / 2.0) % 8.0))

                drawPost(FirstPersonCamera().fromFrame(frame)) {
                    drawer.fill = ColorRGBa(0.5, 0.7, 1.0).shade(0.05)
                    renderStyle.lightDensity = 1.0 - it.beat / 16.0
                    skyBox.draw(drawer, renderStyle)
                    cityFloor.draw(drawer, renderStyle)
                    city.draw(drawer, it.beat, renderStyle)
                }

                post.applyMove = it.beat < 1.0
                post.moveThreshold = 1.0
                post.apply(gbuffer, result, it.time)
                drawer.image(result)
            }
            scheduler.complete()

        }
        run {
            val crowd = CrowdOutro()
            val noiseTable = NoiseTable(type = NoiseType.BROWNIAN, density = 0.8)
            for (i in 0 until 1) {
                val ckf = CameraKeyframer.fromFile("data/tracks/crowd-outro.json")
                scheduler.task(barDuration * 6) {
                    val frame = ckf.frame(it.beat / 2.0
                    ).let { f ->
                        f.copy(
                                position = f.position + noiseTable[it.time * 0.43] * Vector3(1.0, 1.0, 0.1) * 0.005,
                                exposure = (noiseTable[it.time * 14.0].x + 1.0) * rampInOut(0.0, 4 * 5.0, 2.0, it.beat))
                    }

                    post.lut = post.luts.horrorBlue

                    val camera = FirstPersonCamera().fromFrame(frame)
                    drawPost(camera) {
                        val renderStyle = RenderStyle()
                        renderStyle.skyIntensity = 0.05
                        drawer.fill = ColorRGBa.WHITE.shade(0.02)
                        skyBox.draw(drawer, renderStyle)
                        drawer.fill = ColorRGBa.WHITE.shade(0.2)
                        crowd.draw(drawer, it.beat, renderStyle)
                    }
                    post.apply(gbuffer, result, it.time)
                    drawer.image(result)
                }
                scheduler.complete()
            }
        }
    }

    private var freakMode = false
    private var freakDuration = 100
    private var lastFreak = 0L
    private var freakPitch = 44100.0
    private var freakMethod = 0

    override fun draw() {
        if (!dumpVideo) {
            if (channel.getPosition() > 136) {
                freakMode = true
            }
        }

        if (freakMode && System.currentTimeMillis() - lastFreak > freakDuration) {
            if (freakMethod == 0) {
                freakDuration = (Math.random() * 1000).toInt() + 1000

                freakPitch = Math.random() * 22000 + 22000
                channel.setPosition(Math.random() * 130.0)
                channel.setPitch(freakPitch)
                lastFreak = System.currentTimeMillis()

                post.freakMode = true

                if (Math.random() < 0.5) {
                    freakMethod = 1
                }

            } else {
                post.freakMode = false
                freakDuration = (Math.random() * 100).toInt()
                lastFreak = System.currentTimeMillis()

                freakPitch = Math.random() * 42000 + 22000
                channel.setPosition(Math.random() * 130.0)
                channel.setPitch(freakPitch)
                if (Math.random() < 0.01) {
                    freakMethod = 0
                }
            }
        }

        if (freakMode && freakMethod == 0) {
            post.freakMode = true
        }

        if (!freakMode) {
            post.freakMode = false
        }

        if (freakMode && freakMethod == 0) {
            channel.setPitch(freakPitch)
            freakPitch *= 0.99
        }

        finalTarget.bind()

        drawer.ortho(finalTarget)
        drawer.background(ColorRGBa.BLACK)
        val time = if (dumpVideo) frameClock.time / 1000.0 else channel.getPosition()
        scheduler.update(time)
        if (showDebug) {
            drawer.ortho()
            drawer.view = Matrix44.IDENTITY
            drawer.fontMap = FontImageMap.fromUrl("file:data/fonts/IBMPlexMono-Bold.ttf", 16.0, window.scale.x)
            drawer.text("position: $time", 40.0, 40.0)
            drawer.image(post.colorLookup.lookup)
        }
        val beat = time * (scheduler.bpm / 60.0)

        drawer.isolated {
            val font16 = FontImageMap.fromUrl("file:data/fonts/CooperHewitt-Heavy.otf", 16.0)

            drawer.fontMap = font16
            val w = Writer(drawer)
            val tw = w.textWidth(subtitles[beat.toInt() / 4])

            val subIndex = beat.toInt() / 4
            if (subIndex != 58) {
                drawer.text(subtitles[subIndex], targetWidth / 2.0 - tw / 2.0, targetHeight - 50.0)
            } else {
                val l = beat / 4.0 - beat.toInt() / 4
                val li = (subtitles[subIndex].length * l).toInt()
                drawer.text(subtitles[subIndex].substring(0, li), targetWidth / 2.0 - tw / 2.0, targetHeight - 50.0)

            }
        }
        finalTarget.unbind()
        drawer.ortho(finalTarget)
        drawer.image(finalTarget.colorBuffer(0))

        if (dumpVideo) {
//            videoWriter.frame(finalTarget.colorBuffer(0))
            frameClock.next()
        }
    }
}

fun main(args: Array<String>) {
    initBass()
    application(Demo(), configuration {
        title = "LIMP NINJA - Strap"
        width = 1280
        height = 720
        fullscreen = true
        width = -1
        height = -1
        hideCursor = true
    })
}