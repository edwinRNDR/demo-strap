package rndr.studio.demo.sketches

import org.openrndr.Program
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.configuration
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import scenes.Crowd
import scenes.RenderStyle
import scenes.ShadowFloor
import scenes.SkyBox
import studio.rndr.post.PostProcessor

class ShadowMap001: Program() {

    var drawFunc : ()-> Unit = {}

    override fun setup() {

        val lightMap = renderTarget(4096, 4096) {
            colorBuffer(type=ColorType.FLOAT32)
            depthBuffer()
        }
        val crowd = Crowd()
        val post = PostProcessor(width, height)
        val gbuffer = post.createGBuffer()
        val shadowFloor = ShadowFloor()
        val result = colorBuffer(width, height)
        var skyBox = SkyBox()

        drawFunc = {


            var lightProj = Matrix44.IDENTITY
            var lightView = Matrix44.IDENTITY
            drawer.isolatedWithTarget(lightMap) {
                drawer.cullTestPass = CullTestPass.BACK
                drawer.background(ColorRGBa.BLACK)
                drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
                drawer.depthWrite = true
                drawer.ortho(-20.0, 20.0, -20.0, 20.0, 0.0, 50.0)
                drawer.lookAt(Vector3(Math.cos(seconds)*10.0, 20.0 + Math.sin(seconds*0.43)*3.0, 10.0), Vector3(0.0, 0.0, 0.0))
                crowd.drawShadow(drawer)
                lightView = drawer.view
                lightProj = drawer.projection
            }
            drawer.isolatedWithTarget(gbuffer) {


                drawer.background(ColorRGBa.BLACK)


                drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
                drawer.depthWrite = true
                drawer.perspective(90.0, 1280.0 / 720.0, 0.1, 1000.0)
                drawer.lookAt(Vector3((mouse.position.x/width-0.5)*20.0, 5.0, 5.0), Vector3(0.0, 0.0, 0.0))


                val renderStyle = RenderStyle()
                renderStyle.skyIntensity = 1.0

                drawer.fill = ColorRGBa(0.5,0.7,1.0).shade(0.25)
                skyBox.draw(drawer, renderStyle)
                crowd.draw(drawer, lightMap.colorBuffer(0), lightProj, lightView)

                shadowFloor.draw(drawer, lightMap.colorBuffer(0), lightProj, lightView)

                post.projection = drawer.projection
            }

            //drawer.image(lightMap.colorBuffer(0))
            post.apply(gbuffer, result)
            post.focalPlane = 4.0
            drawer.image(gbuffer.colorBuffer(0))
drawer.image(result)


        }

    }

    override fun draw() {
        drawFunc()
    }

}

fun main(args: Array<String>) {

    application( ShadowMap001(), configuration {
        width = 1280
        height = 720
    })

}