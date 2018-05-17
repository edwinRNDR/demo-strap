package scenes

import modeling.extrudeContour
import org.openrndr.animatable.Animatable
import org.openrndr.animatable.easing.Easing
import org.openrndr.draw.*
import org.openrndr.math.*
import org.openrndr.math.transforms.rotate
import org.openrndr.math.transforms.transform
import org.openrndr.math.transforms.translate
import org.openrndr.shape.Rectangle
import org.openrndr.shape.ShapeContour
import org.openrndr.svg.loadSVG
import java.io.File

class Crawlers : CrawlersBase() {

    val paths: List<ShapeContour>

    init {
        val c = loadSVG(File("data/paths/paths-3.svg").readText())
        paths = c.findShapes().map { it.shape.outline }

        paths.forEach {

            val t = transform {

                translate(Vector2(-50.0, -50.0))

            }

            walkers += PathWalker(it.transform(t).sampleEquidistant(80), speed = 10.0).apply {
                root.vertexBuffer = body
                x = it.position(0.0).x
                z = it.position(0.0).y
            }

        }

    }
}

class MegaCrawlers : CrawlersBase() {
    init {
        for (i in 0..0) {
            walkers += StillWalker().apply {
                root.vertexBuffer = body
            }
        }
    }

    override fun drawShadow(drawer: Drawer, time: Double) {

        drawer.isolated {
            drawer.model *= transform {
                scale(40.0)
            }
            super.drawShadow(drawer, time)

        }
    }

    override fun draw(drawer: Drawer, time: Double, renderStyle: RenderStyle, update: Boolean) {
        drawer.isolated {
            drawer.model *= transform {
                scale(40.0)
            }
            super.draw(drawer, time, renderStyle, update)
        }
    }
}

class MegaMarchingCrawlers : CrawlersBase() {
    init {
        val c = loadSVG(File("data/paths/paths-2.svg").readText())
        val paths = c.findShapes().map { it.shape.outline }

        val it = paths[0].transform(transform {

            translate(Vector2(-8.0, -25.0))

        }).sampleEquidistant(80)


        walkers += PathWalker2(it, 0.45).apply {
            root.vertexBuffer = body
            x = it.position(0.0).x
            z = it.position(0.0).y
        }


    }

    override fun drawShadow(drawer: Drawer, time: Double) {

        drawer.isolated {
            drawer.model *= transform {
                scale(40.0)
            }
            super.drawShadow(drawer, time)

        }
    }

    override fun draw(drawer: Drawer, time: Double, renderStyle: RenderStyle, update: Boolean) {
        drawer.isolated {
            drawer.model *= transform {
                scale(40.0)
            }
            super.draw(drawer, time, renderStyle, update)
        }
    }
}


class StillWalker() : Walker() {

    init {
        updatePositionAndDirection(0.0)
    }

    override fun updatePositionAndDirection(time: Double): Boolean {
        x = 0.0
        z = 0.0

        direction = Vector3(0.0, 0.0, 1.0)
        smoothDirection = direction

        if (!hasAnimations()) {
            animate("dummy", 1.0, 250)
            return true
        } else {
            return false
        }
    }
}

class PathWalker(val path: ShapeContour, val speed: Double = 1.0) : Walker() {
    init {
        updatePositionAndDirection(0.0)
    }

    override fun updatePositionAndDirection(time: Double): Boolean {
        val p = path.position(speed * time / 150.0)
        val d = path.normal(speed * time / 150.0)
        val dp = d.perpendicular

        direction = Vector3(dp.x, 0.0, dp.y).normalized
        smoothDirection = smoothDirection * 0.99 + direction * 0.01

        x = p.x
        z = p.y

        if (!hasAnimations()) {
            animate("dummy", 1.0, (250 / speed).toLong())
            return true
        } else {
            return false
        }
    }
}

class PathWalker2(val path: ShapeContour, val speed: Double = 1.0) : Walker() {
    init {
        updatePositionAndDirection(0.0)
    }

    var lastTime = 0.0
    override fun updatePositionAndDirection(time: Double): Boolean {
        val t = Math.floor(time / 2)
        val tf = time / 2 - t

        val stf = smoothstep(0.0, 0.5, tf)

        val stime = (t + stf) * 2

        val p = path.position(speed * stime / 150.0)
        val d = path.normal(speed * stime / 150.0)
        val dp = d.perpendicular

        legStepSize = 1.0
        legOffsetSize = 2.5
        direction = Vector3(dp.x, 0.0, dp.y).normalized

        if (time < lastTime) {
            smoothDirection = direction
        }

        smoothDirection = (direction * smoothDirection * 0.9999 + direction * 0.0001).normalized
        lastTime = time

        x = p.x
        z = p.y

        if (!hasAnimations()) {
            animate("dummy", 1.0, (250 / speed).toLong())
            return true
        } else {
            return false
        }
    }
}

class CrawlersIntro : CrawlersBase() {
    val paths: List<ShapeContour>

    init {
        val c = loadSVG(File("data/paths/paths-1.svg").readText())
        paths = c.findShapes().map { it.shape.outline }

        paths.forEach {
            walkers += PathWalker(it.sampleEquidistant(80)).apply {
                root.vertexBuffer = body
                x = it.position(0.0).x
                z = it.position(0.0).y
            }
        }
    }
}

open class CrawlersBase(width: Double = 50.0, depth: Double = 50.0) {
    val irradiance = Cubemap.fromUrl("file:data/textures/garage_iem.dds")
    val normalMap = ColorBuffer.fromUrl("file:data/textures/ground_normal.png")

    val walkers = mutableListOf<Walker>()
    val body: VertexBuffer
    val format = vertexFormat {
        position(3)
        normal(3)
        attribute("id", 1, VertexElementType.FLOAT32)
        attribute("center", 3, VertexElementType.FLOAT32)
    }

    init {
        body = vertexBuffer(format, 100)
        extrudeContour(Rectangle(Vector2(-2.0, -1.5), 4.0, 1.0).contour, 4.0, 0.0, body.shadow.writer(), true, true, -2.0)
        body.shadow.writer().rewind()
        body.shadow.upload()
    }

    open fun drawShadow(drawer: Drawer, time: Double) {
        drawer.isolated {
            drawer.depthWrite = true
            drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
            drawer.shadeStyle = shadeStyle {
                fragmentTransform = """
                x_fill.rgb = vec3(v_viewPosition);
                x_fill.r = 1.0;
                x_fill.g = 1.0;
                x_fill.a = 1.0;
            """
            }
            walkers.forEachIndexed { index, it ->
                it.update(time)
                drawer.shadeStyle?.parameter("id", index.toFloat())
                drawNode(drawer, it.root)
            }
        }
    }

    open fun draw(drawer: Drawer, time: Double, renderStyle: RenderStyle = RenderStyle(), update: Boolean = true) {
        val gbuffer = RenderTarget.active

        drawer.isolated {
            drawer.depthWrite = true
            drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
            drawer.shadeStyle = shadeStyle {
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
                """
                vertexTransform = """
                    previousView = (p_previousModelView * vec4(x_position,1.0));
                    previousClip = u_projectionMatrix * previousView;
                    currentClip = u_projectionMatrix * u_viewMatrix * u_modelMatrix * vec4(x_position, 1.0);
                """

                fragmentTransform = """
                    vec2 uv = va_position.xz*0.1;
//                    vec3 normal = normalize(texture(p_normalMap, uv).xzy - vec3(0.5, 0.5, 0.5));

                    mat3 tbn = mat3(u_modelNormalMatrix * u_viewNormalMatrix);
                    mat3 tbnW = mat3(u_modelNormalMatrix);

                    x_fill.rgb += max(0.0, v_worldNormal.y);
                    x_fill.rgb = pow(texture(p_irradiance, normalize(v_worldNormal)).rgb, vec3(2.2))*vec3(0.4, 0.05, 0.02) * 20.0; //*(11.0 + 9.0*cos(p_id+p_time*4.0));
                    x_fill.a = 1.0;
                    o_position.xyz = v_viewPosition.xyz;
                    o_position.w = 1.0;
                    o_normal.xyz = v_viewNormal.xyz;
                    o_normal.w = 1.0;
                    o_velocity.xy = (currentClip/currentClip.w - previousClip/previousClip.w).xy*vec2(1280, 720) * 0.08; //p_velocityScale;
//                    o_velocity.z = 0.cos(p_id + p_time*5.0 + va_position.y*3.0) * 0.5 + 0.5;

//                                        x_fill *= max(1.0 + v_viewPosition.z/100.0, 0.0);

                """
                normalMap.wrapV = WrapMode.REPEAT
                normalMap.wrapU = WrapMode.REPEAT
                parameter("irradiance", irradiance)
                parameter("normalMap", normalMap)
                parameter("time", Math.random())
                parameter("velocityScale", renderStyle.velocityScale)
                parameter("time", time)
                parameter("id", 0.0)
                output("position", gbuffer.colorBufferIndex("position"))
                output("normal", gbuffer.colorBufferIndex("normal"))
                output("velocity", gbuffer.colorBufferIndex("velocity"))
            }
            walkers.forEachIndexed { index, it ->
                if (update) {
                    it.update(time)
                }
                drawer.shadeStyle?.parameter("id", index.toFloat())
                drawNode(drawer, it.root)
            }
        }
    }
}

private fun drawNode(drawer: Drawer, node: SceneNode) {
    drawer.pushModel()
    drawer.model *= node.transform
    drawer.model *= node.joint.matrix

    node.vertexBuffer?.let {
        if (drawer.shadeStyle?.outputs?.size ?: 0 > 0) {
            drawer.shadeStyle?.parameter("previousModelView", node.previousModelView)
        }
        drawer.vertexBuffer(it, DrawPrimitive.TRIANGLES)
        if (drawer.shadeStyle?.outputs?.size ?: 0 > 0) {
            node.previousModelView = drawer.view * drawer.model
        }
    }
    for (child in node.children) {
        drawNode(drawer, child)
    }
    drawer.popModel()
}

class Joint(val minX: Double = -Math.PI / 4, val maxX: Double = Math.PI / 4, val minY: Double = 0.0, val maxY: Double = 1.0, val minZ: Double = -Math.PI / 4, val maxZ: Double = Math.PI / 4) {
    var x: Double = 0.0
    var y: Double = 0.0
    var z: Double = 0.0

    var tx: Double = 0.0
    var ty: Double = 0.0
    var tz: Double = 0.0

    val matrix
        get() = Quaternion.fromAnglesRadian(x, y, z).matrix
}

class SceneNode(var transform: Matrix44 = Matrix44.IDENTITY, val joint: Joint = Joint()) {
    val children = mutableListOf<SceneNode>()
    var parent: SceneNode? = null
    var vertexBuffer: VertexBuffer? = null
    var previousModelView = Matrix44.IDENTITY
    val ikChain: List<SceneNode>
        get() {
            val result = mutableListOf<SceneNode>()
            var current = this
            while (true) {
                result.add(current)
                if (current.parent != null) {
                    current = current.parent!!
                } else {
                    break
                }
            }
            return result
        }
}

class Leg(val offset: Vector3) : Animatable() {
    var footX: Double = offset.x
    var footY: Double = offset.y
    var footZ: Double = offset.z

    fun stepTo(position: Vector3, duration: Long) {
        animate(::footY, position.y + 0.5, duration, Easing.CubicInOut)
        complete()

        animate(::footX, position.x, duration, Easing.CubicInOut)
        animate(::footZ, position.z, duration, Easing.CubicInOut)
        complete()

        animate(::footY, position.y, duration, Easing.CubicInOut)
        complete()
    }

    val position: Vector3 get() = Vector3(footX, footY, footZ)
}

open class Walker : Animatable() {
    val root: SceneNode = SceneNode(joint = Joint(0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
    val endPoints = mutableListOf<SceneNode>()

    var x: Double = (Math.random() - 0.5) * 40.0
    var y: Double = 3.5
    var z: Double = (Math.random() - 0.5) * 40.0
    var position = Vector3(0.0, 0.0, 0.0)
    val legs = mutableListOf<Leg>()

    var smoothDirection = Vector3(0.0, 0.0, 1.0)
    var direction = Vector3(Math.random() - 0.5, 0.0, Math.random() - 0.5).normalized
    val leg: VertexBuffer

    var smoothPosition = Vector3(x, y, z)

    init {
        val format = vertexFormat {
            position(3)
            normal(3)
            attribute("id", 1, VertexElementType.FLOAT32)
            attribute("center", 3, VertexElementType.FLOAT32)
        }
        leg = vertexBuffer(format, 100)
        extrudeContour(Rectangle(Vector2(-0.1, 0.0), 0.2, -1.0).contour, 0.2, 0.0, leg.shadow.writer(), true, true, -0.1)
        leg.shadow.writer().rewind()
        leg.shadow.upload()

        addLeg(Vector3(2.0, 0.0, 2.0))
        addLeg(Vector3(2.0, 0.0, -2.0))
        addLeg(Vector3(-2.0, 0.0, -2.0))
        addLeg(Vector3(-2.0, 0.0, 2.0))
        animate("dummy", 1.0, (Math.random() * 2000.0).toLong())
    }

    fun sign(x: Double): Double {
        if (x > 0.0) {
            return 1.0
        } else if (x < 0.0) {
            return -1.0
        } else {
            return 0.0
        }
    }

    fun addLeg(offset: Vector3) {
        var current = root

        val minY = if (offset.z < 0.0) 0.0 else -1.0
        val maxY = if (offset.z < 0.0) 1.0 else 0.0

        for (i in 0..2) {
            val n = SceneNode(
                    transform {
                        translate(Vector3(0.0, -1.0, 0.0) + if (i == 0) offset else Vector3.ZERO)
                        if (i == 0)
                            rotate(Vector3.UNIT_Z, 45.0 * sign(offset.x))
                    }, joint = Joint(minY = minY, maxY = maxY)
            )
            n.vertexBuffer = leg
            current.children.add(n)
            n.parent = current
            current = n
        }
        endPoints.add(current)
        legs.add(Leg(offset + Vector3(0.0, 0.0, 0.0)))
    }

    var legIndex = 0

    open fun updatePositionAndDirection(time: Double): Boolean {
        val duration = 250L
        val speed = 2.0
        val mod = if (legIndex % 2 == 1) 2.0 else 0.0

        if (!hasAnimations()) {
            animate(::x, x + mod * speed * direction.x / 2, duration, Easing.QuadInOut)//, Easing.CubicInOut)//, Easing.CubicInOut)
            animate(::z, z + mod * speed * direction.z / 2, duration, Easing.QuadInOut)//, Easing.CubicInOut)//, Easing.CubicInOut)

            if (Math.random() < 0.1) {
                if (Math.random() < 0.5)
                    direction = rotate(Vector3.UNIT_Y, 5.0) * direction
                else {
                    direction = rotate(Vector3.UNIT_Y, -5.0) * direction
                }
            }
            if (x < -50.0 && direction.x < 0.0) {
                direction *= -1.0
            }

            if (x > 50.0 && direction.x > 0.0) {
                direction *= -1.0
            }

            if (z < -50.0 && direction.z < 0.0) {
                direction *= -1.0
            }

            if (z > 50.0 && direction.z > 0.0) {
                direction *= -1.0
            }
            return true
        } else {
            return false
        }
        smoothDirection = (smoothDirection * 0.95 + direction * 0.05).normalized
        smoothPosition = smoothPosition * 0.95 + Vector3(x, y, z) * 0.05
    }

    var legStepSize = 2.0
    var legOffsetSize = 1.5

    fun update(time: Double) {
        updateAnimation()

        legs.forEach { it.updateAnimation() }
        if (true) {
            val right = direction.cross(Vector3.UNIT_Y)
            val duration = 250L
            val speed = 2.0
            val basis = Matrix44.fromColumnVectors(right.xyz0, Vector3.UNIT_Y.xyz0, smoothDirection.xyz0, Vector4.UNIT_W)

            if (updatePositionAndDirection(time)) {
                legs[(legIndex) % 4].apply {
                    val position = Vector3(x, y, z)
                    stepTo((position + (basis * (offset.xyz1 * legOffsetSize)).div + direction * legStepSize * speed).copy(y = 0.0), duration)
                }
                legIndex = (legIndex + 1)
            }
        }

        val smoothRight = smoothDirection.cross(Vector3.UNIT_Y)
        val sm = Matrix44.fromColumnVectors(smoothRight.xyz0, Vector3.UNIT_Y.xyz0, smoothDirection.xyz0.normalized, Vector4.UNIT_W)

        root.transform =
                transform {
                    translate(position + Vector3(x, y, z))
                    multiply(sm)
                    rotate(Vector3.UNIT_Z, Math.cos(x) * 10.0)
                }
        for (j in 0 until endPoints.size) {
            push(endPoints[j])
            val chain = endPoints[j].ikChain.map { it.joint }
            var oldError = 10000.0
            for (i in 0 until 10) {
                var newError = optimize(endPoints[j], legs[j].position, chain)
                if (newError / oldError > 0.99)
                    break
                oldError = newError
            }
            relax(endPoints[j])
            pop(endPoints[j])
        }
    }
}

fun push(endPoint: SceneNode) {
    val chain = endPoint.ikChain.map { it.joint }
    chain.forEach {
        it.tx = it.x
        it.ty = it.y
        it.tz = it.z
    }
}

fun pop(endPoint: SceneNode) {
    val chain = endPoint.ikChain.map { it.joint }
    chain.forEach {
        it.x = (it.x - it.tx).coerceIn(-0.1 / 2, 0.1 / 2) + it.tx
        it.y = (it.y - it.ty).coerceIn(-0.1 / 2, 0.1 / 2) + it.ty
        it.z = (it.z - it.tz).coerceIn(-0.1 / 2, 0.1 / 2) + it.tz
    }
}

fun optimize(endPoint: SceneNode, target: Vector3, chain: List<Joint>): Double {
    var currentError = (forward(endPoint) - target).length
    //val chain = endPoint.ikChain.map { it.joint }
    val delta = 0.01
    val l = 0.01

    val step = mutableListOf<Double>()

    for (joint in chain) {
        joint.x += delta
        step += ((forward(endPoint) - target).length - currentError) / delta
        joint.x -= delta

        joint.y += delta
        step += ((forward(endPoint) - target).length - currentError) / delta
        joint.y -= delta

        joint.z += delta
        step += ((forward(endPoint) - target).length - currentError) / delta
        joint.z -= delta
    }

    chain.forEachIndexed { index, joint ->
        joint.x -= step[index * 3] * l
        joint.y -= step[index * 3 + 1] * l
        joint.z -= step[index * 3 + 2] * l

        joint.x = joint.x.coerceIn(joint.minX, joint.maxX)
        joint.y = joint.y.coerceIn(joint.minY, joint.maxY)
        joint.z = joint.z.coerceIn(joint.minZ, joint.maxZ)
    }
    currentError = (forward(endPoint) - target).length
    return currentError
}

fun relax(endPoint: SceneNode) {
    val chain = endPoint.ikChain.map { it.joint }
    val step = DoubleArray(chain.size * 3)
    chain.forEachIndexed { index, joint ->
        if (index > 0) {
            step[index * 3] += chain[index - 1].x - joint.x
            step[index * 3 + 1] += chain[index - 1].y - joint.y
            step[index * 3 + 2] += chain[index - 1].z - joint.z
        }
        if (index < chain.size - 1) {
            step[index * 3] += chain[index + 1].x - joint.x
            step[index * 3 + 1] += chain[index + 1].y - joint.y
            step[index * 3 + 2] += chain[index + 1].z - joint.z
        }
    }
    val l = 0.1
    chain.forEachIndexed { index, joint ->
        joint.x += step[index * 3] * l
        joint.y += step[index * 3 + 1] * l
        joint.z += step[index * 3 + 2] * l
    }
}

private fun forward(node: SceneNode): Vector3 {
    val chain = node.ikChain.reversed()
    var transform = Matrix44.IDENTITY
    chain.forEach {
        transform *= it.transform
        transform *= it.joint.matrix
    }
    transform *= translate(0.0, -1.0, .00)
    return (transform * Vector4(0.0, 0.0, 0.0, 1.0)).xyz
}
