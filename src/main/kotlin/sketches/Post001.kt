package sketches.post001

import org.openrndr.Program
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.configuration
import org.openrndr.draw.*
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.transform
import scenes.BackBox
import scenes.Crawlers
import scenes.CubeField
import scenes.Floor
import studio.rndr.post.PostProcessor

class Post001 : Program() {

    lateinit var post: PostProcessor
    lateinit var gbuffer: RenderTarget
    lateinit var postProcessed: ColorBuffer
    lateinit var floor: Floor
    lateinit var cubeField: CubeField
    lateinit var backBox: BackBox
    lateinit var crawlers: Crawlers
    override fun setup() {
        post = PostProcessor(1280, 720)
        gbuffer = post.createGBuffer()
        postProcessed = colorBuffer(1280, 720)
        floor = Floor()
        cubeField = CubeField()
        backBox = BackBox(500.0)

        crawlers = Crawlers()
        post.enableHDR = false
        post.aperture = 0.2
        post.focalPlane = 8.0
        post.focalLength = 18.0
        post.exposure = 1.0


        post.applyMove = false
    }

    override fun draw() {
        drawer.isolatedWithTarget(gbuffer) {
            drawer.depthWrite = true
            drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
            drawer.background(ColorRGBa.BLACK)

            drawer.perspective(60.0, 1280.0/720.0, 0.1, 1400.0)


            val segment = (seconds.toInt() % 15)/5

//            if (segment == 0) {
//                val cp = crawlers.walkers[0].smoothPosition
//                drawer.lookAt(cp + crawlers.walkers[0].smoothDirection * 5.0 + Vector3(0.0, -2.0, 0.0), cp)
//            } else if (segment == 1) {
//                val cp = crawlers.walkers[0].smoothPosition
//                drawer.lookAt(cp - crawlers.walkers[0].smoothDirection * 5.0 + Vector3(0.0, 2.0, 0.0), cp)
//
//            } else if (segment == 2) {
//                drawer.lookAt(Vector3(0.0, 50.0, 10.0), Vector3(0.0, 0.0, 0.0))
//            }

            drawer.model *= transform {
                rotate(Vector3.UNIT_X, 30.0)
                translate(Vector3(0.1 * (mouse.position.x - width / 2.0), 0.1 * (mouse.position.y - height / 2.0) - 8.0, -5.0))
            }

            post.projection = drawer.projection
            backBox.draw(drawer)
            drawer.fill = ColorRGBa.WHITE.shade(0.05)
            floor.draw(drawer)
            drawer.fill = ColorRGBa.WHITE.shade(0.25)
            cubeField.draw(drawer)
            crawlers.draw(drawer, seconds)
        }
        post.apply(gbuffer, postProcessed, seconds)
        drawer.image(postProcessed)

    }

}


fun main(args: Array<String>) {
    application(
            Post001(), configuration {
        width = 1280
        height = 720

    }
    )
}