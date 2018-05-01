package rndr.studio.demo

import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import scenes.*
import studio.rndr.camera.CameraKeyframer
import studio.rndr.camera.FirstPersonCamera
import studio.rndr.demo.bass.Channel
import studio.rndr.demo.bass.initBass
import studio.rndr.demo.bass.openStream
import studio.rndr.demosystem.Scheduler
import studio.rndr.post.PostProcessor
import studio.rndr.screens.City


fun rampInOut(start:Double,end:Double,accel:Double,time:Double):Double {
    val t = time-start
    val d = end-start
    val a = 1.0 / (accel/d)
    val tn = t /d

    return if (tn>=0 && tn <=1.0) {
        (a * tn).coerceIn(0.0, 1.0) * (a* (1.0-tn)).coerceIn(0.0, 1.0)
    } else {
        0.0
    }

}

/**
 * Demo skeleton
 */
class Demo : Program() {

    lateinit var channel: Channel
    private val scheduler = Scheduler(112.0)

    var showDebug = false

    val targetWidth = 1280
    val targetHeight = 720

    lateinit var gbuffer: RenderTarget
    lateinit var post: PostProcessor
    lateinit var result: ColorBuffer
    override fun setup() {
        initBass()
        println("loading music")
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
        channel.play()
    }

    /**
     * Build the demo's schedule
     */
    fun schedule() {

        val beatDuration = 60.0 / scheduler.bpm
        val barDuration = beatDuration * 4
        val patternDuration = barDuration * 4

        val backBox = BackBox(1400.0)
        val floor = Floor()
        val city = City()
        val skyBox = SkyBox()
        val cityFloor = CityFloor(city.irradiance)


        fun drawPost(camera:FirstPersonCamera, drawFunction:()->(Unit)) {
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
            val cameras = (1..4).map { CameraKeyframer.fromFile("display-${String.format("%02d", it)}.json") }

            val displayLines = DisplayLines()
            val texturedRectangle = TexturedRectangle(displayLines.rt.colorBuffer(0))
            scheduler.task(barDuration * 8) {

                post.lut = post.luts.purple03

                val camera = FirstPersonCamera().fromFrame(cameras[it.bar.toInt()/2].frame(it.beat%8.0)
                        .copy(exposure = rampInOut(0.0, 8.0, 1.0, it.beat%8.0))
                )
                drawPost(camera) {
                    displayLines.draw(drawer, it.beat)
                    displayLines.rt.colorBuffer(0).generateMipmaps()

                    val renderStyle = RenderStyle()
                    drawer.fill = ColorRGBa.BLACK
                    backBox.draw(drawer)
                    drawer.fill = ColorRGBa.WHITE.shade(0.1)
                    floor.draw(drawer)
                    texturedRectangle.draw(drawer)
                }
                post.apply(gbuffer, result, it.time)
                drawer.image(result)

            }
            scheduler.complete()
        }

        run {

            val crowd = CrowdIntro()
            for(i in 1 until 5) {
                val ckf = CameraKeyframer.fromFile("person-${String.format("%02d", i)}.json")
                scheduler.task(barDuration*2) {
                    val frame = ckf.frame(it.beat/2.0
                    ).copy(exposure = rampInOut(0.0, 7.0, 1.0, it.beat%8.0))

                    post.lut = post.luts.cripWinter


                    val camera = FirstPersonCamera().fromFrame(frame)
                    drawPost(camera) {
                        drawer.fill = ColorRGBa.WHITE.shade(0.02)
                        skyBox.draw(drawer)
                        cityFloor.draw(drawer)
                        drawer.fill = ColorRGBa.WHITE.shade(0.1+0.7*rampInOut(2.0, 8.0, 0.25, it.beat)
                        +0.5*rampInOut(1.75, 2.75, 0.5, it.beat)
                        )
                        crowd.draw(drawer, it.beat)
                    }
                    if (it.beat > 6.0) {
                        post.moveThreshold = 140.0
                        post.applyMove = true
                    } else {
                        post.applyMove = false
                    }
                    post.apply(gbuffer, result, it.time)
                    drawer.image(result)



                }
                scheduler.complete()
            }
        }

        run {
            val crawlers = CrawlersIntro()
            val cameras = (0..1).map { CameraKeyframer.fromFile("crawler-intro-${String.format("%02d", it)}.json") }

            scheduler.task(patternDuration*2) {

                post.lut = post.luts.neutral

                if (it.beat < 4.0) {
                    post.moveThreshold = 140.0
                    post.applyMove = true
                } else {
                    post.applyMove = false
                }


                val camera = cameras[it.bar.toInt()%2].frame(it.beat)
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
            val crawlers =  Crawlers()
            val crowd = Crowd02()
            val cameras = (1..2).map { CameraKeyframer.fromFile("crawler-carnage-${String.format("%02d", it)}.json") }

            scheduler.task(patternDuration*2) {
                post.lut = post.luts.horrorBlue

                val camera = cameras[it.bar.toInt()%2].frame(it.beat)
                drawPost(FirstPersonCamera().fromFrame(camera)) {


                    drawer.fill = ColorRGBa.WHITE.shade(0.05)
                    backBox.draw(drawer)
                    floor.draw(drawer)

                    crawlers.draw(drawer, it.beat)
                    crowd.draw(drawer)
                }
                post.apply(gbuffer, result, it.time)
                drawer.image(result)

            }
            scheduler.complete()
        }
        run {

            val megaCrawlers = MegaCrawlers()
            val cameras = (1..4).map { CameraKeyframer.fromFile("city-scan-${String.format("%02d", it)}.json") }
            scheduler.task(patternDuration*2) {

                post.lut = post.luts.neutral

                val renderStyle = RenderStyle()
                renderStyle.skyIntensity = when (it.beat) {
                    in 0.0 .. 8.0 -> 0.25
                    in 8.0 .. 16.0 -> 0.125
                    else -> 0.0685
                }

                val frame = cameras[(it.bar/2).toInt()%4].frame(it.beat%8.0
                ).copy(exposure = rampInOut(0.0, 8.0, 1.0, it.beat%8.0))

                drawPost(FirstPersonCamera().fromFrame(frame)) {
                    drawer.fill = ColorRGBa(0.5,0.7,1.0).shade(0.05)
                    skyBox.draw(drawer, renderStyle)
                    cityFloor.draw(drawer, renderStyle)
                    city.draw(drawer, it.beat, renderStyle)
                    megaCrawlers.draw(drawer,it.beat)
                }
                post.apply(gbuffer, result, it.time)
                drawer.image(result)
            }
            scheduler.complete()

        }
        run {
            val megaCrawlers = MegaCrawlers()
            val cameras = (1..4).map { CameraKeyframer.fromFile("city-fly-${String.format("%02d", it)}.json") }
            scheduler.task(patternDuration*2) {

                post.lut = post.luts.neutral

                val frame = cameras[(it.bar/2).toInt()%4].frame(it.beat%8.0)
                        .copy(exposure = rampInOut(0.0, 8.0, 1.0, it.beat%8.0))

                drawPost(FirstPersonCamera().fromFrame(frame)) {
                    val renderStyle = RenderStyle()
                    renderStyle.skyIntensity = 0.0685
                    drawer.fill = ColorRGBa(0.5,0.7,1.0).shade(0.05)
                    skyBox.draw(drawer, renderStyle)
                    cityFloor.draw(drawer, renderStyle)
                    city.draw(drawer, it.beat, renderStyle)
                    megaCrawlers.draw(drawer,it.beat)
                }
                post.apply(gbuffer, result, it.time)
                drawer.image(result)
            }
            scheduler.complete()

        }
        run {
            val megaCrawlers = MegaMarchingCrawlers()
            val cameras = (1..4).map { CameraKeyframer.fromFile("city-march-${String.format("%02d", it)}.json") }
            scheduler.task(patternDuration*2) {
                val frame = cameras[(it.bar/2).toInt()%4].frame(it.beat%8.0)
                        .copy(exposure = rampInOut(0.0, 8.0, 1.0, it.beat%8.0))


                post.lut = post.luts.purple03

                drawPost(FirstPersonCamera().fromFrame(frame)) {
                    val renderStyle = RenderStyle()


                    renderStyle.skyIntensity = if (it.beat%2.0 < 1.0) 0.0685 else (1.0-((it.beat%2.0)-1.0)) * 0.4 + 0.0685
                    drawer.fill = ColorRGBa(0.5,0.7,1.0).shade(0.05)
                    skyBox.draw(drawer, renderStyle)
                    cityFloor.draw(drawer, renderStyle)
                    city.draw(drawer, it.beat, renderStyle)
                    megaCrawlers.draw(drawer,it.beat%8.0, renderStyle)
                }
                post.applyMove = (it.beat%2.0) < 1.0

                if (it.beat%2.0 >= 0.5) {
                    post.moveThreshold = 2.0-((it.beat-0.5) *2.0)*2.0;
                } else {
                    post.moveThreshold = 140.0
                }
                post.apply(gbuffer, result, it.time)
                drawer.image(result)
            }
            scheduler.complete()

        }
        run {

            val megaCrawlers = MegaCrawlers()
            val cameras = (4..4).map { CameraKeyframer.fromFile("city-${String.format("%02d", it)}.json") }
            scheduler.task(barDuration*2) {

                post.lut = post.luts.cripWinter

                val renderStyle = RenderStyle()
                renderStyle.skyIntensity = when (it.beat) {
                    in 0.0 .. 8.0 -> 0.25
                    in 8.0 .. 16.0 -> 0.125
                    else -> 0.0685
                }

                val frame = cameras[(it.bar/2).toInt()%4].frame(it.beat%8.0
                ).copy(exposure = rampInOut(0.0, 8.0, 1.0, it.beat%8.0))

                drawPost(FirstPersonCamera().fromFrame(frame)) {
                    drawer.fill = ColorRGBa(0.5,0.7,1.0).shade(0.05)
                    renderStyle.lightDensity = 1.0 - it.beat/8.0
                    skyBox.draw(drawer, renderStyle)
                    cityFloor.draw(drawer, renderStyle)
                    city.draw(drawer, it.beat, renderStyle)
                    //megaCrawlers.draw(drawer,it.beat)
                }
                post.apply(gbuffer, result, it.time)
                drawer.image(result)
            }
            scheduler.complete()

        }


    }

    override fun draw() {
        drawer.background(ColorRGBa.BLACK)

        val time = channel.getPosition()

        scheduler.update(time)

        if (showDebug) {
            drawer.ortho()
            drawer.view = Matrix44.IDENTITY
            drawer.fontMap = FontImageMap.fromUrl("file:data/fonts/IBMPlexMono-Bold.ttf", 16.0, window.scale.x)
            drawer.text("position: ${time}", 40.0, 40.0)
            drawer.image(post.colorLookup.lookup)
        }

    }
}

fun main(args: Array<String>) {
    initBass()
    application(Demo(), configuration {
        width = 1280
        height = 720
    })
}