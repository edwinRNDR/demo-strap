package sketches

import org.openrndr.Program
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.configuration
import org.openrndr.draw.*
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.transform
import scenes.*
import studio.rndr.camera.FirstPersonCamera
import studio.rndr.post.PostProcessor

class Sketch004 : Program() {
    lateinit var crowd: Crowd02
    lateinit var floor: Floor
    lateinit var gbuffer: RenderTarget
    lateinit var post: PostProcessor
    lateinit var filtered: ColorBuffer
    lateinit var backBox: BackBox
    lateinit var cubeField: CubeField
    lateinit var crawlers: Crawlers
    lateinit var camera: FirstPersonCamera
    override fun setup() {
        crowd = Crowd02()
        floor = Floor()
        post = PostProcessor(1280, 720)
        gbuffer = post.createGBuffer()
        filtered = colorBuffer(1280, 720)
        backBox = BackBox(500.0)
        cubeField = CubeField()
        crawlers = Crawlers()
        camera = FirstPersonCamera()
        camera.setup(this)
    }

    override fun draw() {

        post.enableHDR = false
        post.aperture = 0.2
        post.focalPlane = 8.0
        post.focalLength = 18.0
        post.exposure = 1.0

        drawer.isolatedWithTarget(gbuffer) {
            drawer.depthWrite = true
            drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
            drawer.background(ColorRGBa.BLACK)

            drawer.perspective(90.0, 1280.0 / 720.0, 0.1, 1400.0)


            drawer.view = camera.viewMatrix
            post.projection = drawer.projection

            //drawer.lookAt(Vector3(0.0, mouse.position.y, -10.0), Vector3(0.0, 0.0, 0.0))

            drawer.fill = ColorRGBa.BLACK
            backBox.draw(drawer)
            drawer.fill = ColorRGBa.WHITE.shade(0.2)
            floor.draw(drawer)
            crowd.draw(drawer, seconds)
            drawer.fill = ColorRGBa.PINK.shade(0.2)
            crawlers.draw(drawer, seconds)
        }
        post.apply(gbuffer, filtered, seconds)
        drawer.image(filtered)
    }
}

fun main(args: Array<String>) {
    application(Sketch004(), configuration {
        width = 1280
        height = 720
    })
}