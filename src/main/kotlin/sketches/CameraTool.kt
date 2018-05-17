package sketches

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.openrndr.Application
import org.openrndr.Configuration
import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.filter.blend.MultiplyContrast
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.dot
import scenes.*
import studio.rndr.camera.CameraKey
import studio.rndr.camera.CameraKeyframer
import studio.rndr.camera.FirstPersonCamera
import studio.rndr.camera.TweenMode
import studio.rndr.post.PostProcessor
import studio.rndr.screens.City
import java.io.File
import java.time.LocalDateTime

// Text overlays
class CameraTool : Program() {
    lateinit var postProcessor: PostProcessor
    lateinit var result: ColorBuffer
    lateinit var gbuffer: RenderTarget

    val camera = FirstPersonCamera()
    var previousViewMatrix = Matrix44.IDENTITY

    lateinit var overlay: RenderTarget
    lateinit var multiplyContrast: MultiplyContrast

    val cameraKeyFramer = CameraKeyframer()
    var activeTweenMode = TweenMode.LINEAR
    var activeInterval = 1.0
    var replayAnimation = false
    var replayStart = 0.0

    lateinit var font: FontImageMap
    lateinit var overlayFont: FontImageMap

    lateinit var drawFunction: (Double) -> Unit
    override fun setup() {

        val floor = Floor()
        val crowdOutro = CrowdOutro()
        val scene = CrowdIntro()
        val crawlersIntro = CrawlersIntro()
        val crawlers = Crawlers()
        val crowd02 = Crowd02()
        val backBox = BackBox(1000.0)
        val city = City()
        //val megaCrawlers = MegaCrawlers()
        val megaMarchingCrawlers = MegaMarchingCrawlers()

        val skyBox = SkyBox()
        val cityFloor = CityFloor(city.irradiance)


        val displayLines = DisplayLines()
        val texturedRectangle = TexturedRectangle(displayLines.rt.colorBuffer(0))

        drawFunction = fun(time:Double) {

//            displayLines.draw(drawer, time)
//            displayLines.rt.colorBuffer(0).generateMipmaps()
//
            val renderStyle = RenderStyle()
//            drawer.fill = ColorRGBa.BLACK
//            backBox.draw(drawer)
//            drawer.fill = ColorRGBa.WHITE.shade(0.1)
//            floor.draw(drawer)
//            texturedRectangle.draw(drawer)
//


            renderStyle.skyIntensity = 0.125
            drawer.fill = ColorRGBa.WHITE.shade(0.2)
            skyBox.draw(drawer, renderStyle)
            crowdOutro.draw(drawer, time, renderStyle)
//
//            cityFloor.draw(drawer, renderStyle)
//            city.draw(drawer, time, renderStyle)
//            megaMarchingCrawlers.draw(drawer,time)

        }


        font = FontImageMap.fromUrl("file:data/fonts/IBMPLexMono-Bold.ttf", 16.0)
        overlayFont = FontImageMap.fromUrl("file:data/fonts/IBMPLexMono-Bold.ttf", 196.0)

        overlay = renderTarget(width, height) {
            colorBuffer()
        }
        gbuffer = renderTarget(width, height) {
            colorBuffer("albedo", ColorFormat.RGBa, ColorType.FLOAT16)
            colorBuffer("position", ColorFormat.RGBa, ColorType.FLOAT32)
            colorBuffer("normal", ColorFormat.RGBa, ColorType.FLOAT16)
            colorBuffer("velocity", ColorFormat.RG, ColorType.FLOAT16)
            depthBuffer()
        }

        result = colorBuffer(width, height)
        postProcessor = PostProcessor(width, height)
        multiplyContrast = MultiplyContrast()

        camera.setup(this)

        window.drop.listen {
            val json = it.files.first().readText()
            val keys = Gson().fromJson(json, Array<CameraKey>::class.java).toList()
            cameraKeyFramer.keys.clear()
            cameraKeyFramer.keys.addAll(keys)
        }

        mouse.clicked.listen {

            gbuffer.colorBuffer("position").shadow.download()
            val z = gbuffer.colorBuffer("position").shadow.read(mouse.position.x.toInt(), mouse.position.y.toInt()).b
            camera.focalPlane = Math.abs(z)
        }

        keyboard.keyDown.filter { it.name == "m" }.listen {

            postProcessor.applyMove = !postProcessor.applyMove
        }

        keyboard.keyDown.filter { it.name == "a" }.listen {
            var last = -activeInterval
            if (cameraKeyFramer.keys.size > 0) {
                last = cameraKeyFramer.keys.last().time
            }
            cameraKeyFramer.keys.add(CameraKey(camera.position, camera.cameraQuat, camera.fov, camera.aperture, camera.focalPlane, camera.exposure, camera.centerX, camera.centerY, last + activeInterval, activeTweenMode))
        }

        keyboard.keyDown.filter { it.name == "c" }.listen {
            cameraKeyFramer.keys.clear()
            replayAnimation = false
        }


        keyboard.keyDown.filter { it.name == "l" }.listen { activeInterval++ }
        keyboard.keyDown.filter { it.name == "k" }.listen { activeInterval-- }
        keyboard.keyDown.filter { it.name == "p" }.listen {
            activeTweenMode = TweenMode.values()[(activeTweenMode.ordinal + 1) % TweenMode.values().size]
        }

        keyboard.keyDown.filter { it.name == "o" }.listen {
            activeTweenMode = TweenMode.values()[((activeTweenMode.ordinal - 1) % TweenMode.values().size + TweenMode.values().size) % TweenMode.values().size]
        }


        keyboard.keyDown.filter { it.name == "x" }.listen {
            if (cameraKeyFramer.keys.size > 0) {
                cameraKeyFramer.keys.removeAt(cameraKeyFramer.keys.lastIndex)
            }
        }

        keyboard.keyDown.filter { it.name == "r" }.listen {

            replayAnimation = !replayAnimation

            if (replayAnimation) {
                replayStart = seconds
            }

        }

        keyboard.keyDown.filter { it.name == "q" }.listen {
            if (cameraKeyFramer.keys.size > 0) {
                camera.cameraQuat = cameraKeyFramer.keys.last().orientation
            }
        }
        keyboard.keyDown.filter { it.name == "w" }.listen {
            if (cameraKeyFramer.keys.size > 0) {
                camera.position = cameraKeyFramer.keys.last().position
            }
        }



        keyboard.keyDown.filter { it.name == "s" }.listen {
            val dt = LocalDateTime.now()
            val f = File("camera-recording-${dt.month.value}-${dt.dayOfMonth}_${dt.hour}-${dt.minute}.json")
            f.writeText(GsonBuilder().setPrettyPrinting().create().toJson(cameraKeyFramer.keys))

        }
    }


    var animationTime = 0.0

    override fun draw() {
        drawer.background(ColorRGBa.PINK)

        drawer.isolatedWithTarget(gbuffer) {
            drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
            drawer.depthWrite = true
            drawer.background(ColorRGBa.BLACK)
            drawer.perspective(camera.fov, width * 1.0 / height, 0.1, 1400.0)


            if (replayAnimation) {
                animationTime = (seconds - replayStart) * (112.0 / 60.0)
            } else {
                var last = -activeInterval
                if (cameraKeyFramer.keys.size > 0) {
                    last = cameraKeyFramer.keys.last().time
                }
                animationTime = last + activeInterval
            }

            if (replayAnimation) {
                val frame = cameraKeyFramer.frame(animationTime)
                camera.position = frame.position
                camera.cameraQuat = frame.orientation
                camera.fov = frame.fov
                camera.aperture = frame.aperture
                camera.focalPlane = frame.focalPlane
                camera.exposure = frame.exposure
                camera.centerX = frame.centerX
                camera.centerY = frame.centerY
                camera.update()
            }



            drawer.projection = org.openrndr.math.transforms.perspective(camera.fov, width * 1.0 / height, 0.1, 1400.0, camera.centerX * 0.1, camera.centerY * 0.1)

            postProcessor.projection = drawer.projection
            postProcessor.aperture = camera.aperture
            postProcessor.focalPlane = camera.focalPlane
            postProcessor.exposure = camera.exposure

            drawer.view = camera.viewMatrix


            drawFunction(animationTime)

            previousViewMatrix = drawer.view
        }

        postProcessor.apply(gbuffer, result)

////        drawer.withTarget(overlay) {
////            drawer.background(ColorRGBa.TRANSPARENT)
////            drawer.fontMap = overlayFont
////            drawer.fill = ColorRGBa.PINK
////            //gridText(drawer, "${(50.0*seconds).toInt()}", 1, 4)
//        }

        //multiplyContrast.apply(arrayOf(overlay.colorBuffer(0),result),result)
        drawer.image(result)

        drawer.fontMap = font
        drawer.fill = ColorRGBa(1.0, 0.5, 0.0)
        drawer.text("aperture: ${camera.aperture}", 40.0, 100.0)
        drawer.text("exposure: ${camera.exposure}", 40.0, 120.0)
        drawer.text("focal plane: ${camera.focalPlane}", 40.0, 140.0)
        drawer.text("fov: ${camera.fov}", 40.0, 180.0)
        drawer.text("center: ${camera.centerX} ${camera.centerY}", 40.0, 200.0)
        drawer.text("tween: ${activeTweenMode.name}", 40.0, 220.0)
        drawer.text("interval: ${activeInterval}", 40.0, 240.0)


        if (cameraKeyFramer.keys.size > 0) {
            val last = cameraKeyFramer.keys.last()
            drawer.text("last: ${last.time}", 40.0, 260.0)

            drawer.text("distance from last: ${(camera.position - last.position).length}", 40.0, 280.0)
            drawer.text("rotation from last: ${(dot(camera.cameraQuat.normalized, last.orientation.normalized))}", 40.0, 300.0)
        }


        val circles = cameraKeyFramer.keys.map {

            val x = (it.time % 16.0) * 16.0 + (width - 300)
            val y = Math.floor(it.time / 16.0) * 16.0 + 100.0
            Vector2(x, y)
        }

        drawer.circles(circles, 6.0)

    }
}

fun main(args: Array<String>) {
    Application.run(CameraTool(), Configuration().apply {
        width = 1280
        height = 720
    })
}