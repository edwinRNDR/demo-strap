package sketches.ik003

import modeling.extrudeContour
import org.openrndr.Program
import org.openrndr.animatable.Animatable
import org.openrndr.animatable.easing.Easing
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.configuration
import org.openrndr.draw.*
import org.openrndr.math.*
import org.openrndr.math.transforms.rotate
import org.openrndr.math.transforms.rotateZ
import org.openrndr.math.transforms.transform
import org.openrndr.math.transforms.translate
import org.openrndr.shape.Rectangle

class Joint(val minX: Double = -Math.PI / 4, val maxX: Double = Math.PI / 4, val minY: Double = 0.0, val maxY: Double =1.0, val minZ: Double = -Math.PI / 4, val maxZ: Double = Math.PI / 4) {
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

    fun stepTo(position:Vector3) {
        animate(::footY, position.y+0.5 , 250, Easing.CubicInOut)
        complete()

        animate(::footX, position.x, 250, Easing.CubicInOut)
        animate(::footZ, position.z, 250, Easing.CubicInOut)
        complete()

        animate(::footY, position.y, 250, Easing.CubicInOut)
        complete()
    }

    val position:Vector3 get() = Vector3(footX, footY, footZ)
}

class Walker : Animatable() {
    val root: SceneNode = SceneNode(joint = Joint(0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
    val endPoints = mutableListOf<SceneNode>()

    var x: Double = (Math.random()-0.5) * 40.0
    var y: Double = 3.5
    var z: Double = (Math.random()-0.5) * 40.0
    var position = Vector3(0.0, 0.0, 0.0)
    val legs = mutableListOf<Leg>()

    var smoothDirection = Vector3(0.0, 0.0, 1.0)
    var direction = Vector3(Math.random()-0.5, 0.0, Math.random()-0.5).normalized
    val leg: VertexBuffer

    init {
        val format = vertexFormat {
            position(3)
            normal(3)
            attribute("id", 1, VertexElementType.FLOAT32)
            attribute("center", 3, VertexElementType.FLOAT32)
        }
        leg = vertexBuffer(format, 100)

        extrudeContour(Rectangle(Vector2(-0.1, 0.0), 0.2, -1.0).contour, 0.2, 0.0, leg.shadow.writer(), false, true, -0.1)
        leg.shadow.writer().rewind()
        leg.shadow.upload()

        addLeg(Vector3(2.0, 0.0, 2.0))
        addLeg(Vector3(2.0, 0.0, -2.0))
        addLeg(Vector3(-2.0, 0.0, -2.0))
        addLeg(Vector3(-2.0, 0.0, 2.0))
    }

    fun sign(x:Double):Double {
        if (x > 0.0) {
            return 1.0
        }
        else if (x < 0.0) {
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
                        if (i ==0)
                            rotate(Vector3.UNIT_Z, 45.0*sign(offset.x))
                    }, joint= Joint(minY =minY, maxY = maxY)

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
    fun update() {
        updateAnimation()

        legs.forEach { it.updateAnimation() }

        if (!hasAnimations()) {
            val right = direction.cross(Vector3.UNIT_Y)
            val basis = Matrix44.fromColumnVectors(right.xyz0, Vector3.UNIT_Y.xyz0, smoothDirection.xyz0, Vector4.UNIT_W)
            animate(::x, x + direction.x/2, 250)//, Easing.CubicInOut)
            animate(::z, z + direction.z/2, 250)//, Easing.CubicInOut)

            legs[(legIndex)%4].apply {
                val position= Vector3(x, y, z)
                stepTo((position + basis*(offset*1.5) + direction*2.0).copy(y=0.0))
            }

            legIndex = (legIndex+1)

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
        }

        smoothDirection = (smoothDirection * 0.95 + direction *0.05).normalized
        val smoothRight = smoothDirection.cross(Vector3.UNIT_Y)
        val sm = Matrix44.fromColumnVectors(smoothRight.xyz0, Vector3.UNIT_Y.xyz0, smoothDirection.xyz0.normalized, Vector4.UNIT_W)

        root.transform =
                transform {
                    translate(position + Vector3(x, y, z))
                    multiply(sm)
                    rotate(Vector3.UNIT_Z,  Math.cos(x)*10.0)
                }
        for (j in 0 until endPoints.size) {
            push(endPoints[j])
            val chain = endPoints[j].ikChain.map { it.joint }
            var oldError = 10000.0
            for (i in 0 until 10) {
                var newError = optimize(endPoints[j], legs[j].position, chain)
                if (newError / oldError > 0.98)
                    break
                oldError = newError
            }
            relax(endPoints[j])
            pop(endPoints[j])
        }
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

class IK003 : Program() {
    lateinit var body: VertexBuffer
    lateinit var ground: VertexBuffer

    val walkers = mutableListOf<Walker>()
    override fun setup() {
        val format = vertexFormat {
            position(3)
            normal(3)
            attribute("id", 1, VertexElementType.FLOAT32)
            attribute("center", 3, VertexElementType.FLOAT32)
        }

        for (i in 0 .. 20) {
            walkers += Walker()
        }

        ground = vertexBuffer(format, 100)
        body = vertexBuffer(format, 100)

        extrudeContour(Rectangle(Vector2(-2.0, -1.5), 4.0, 1.0).contour, 4.0, 0.0, body.shadow.writer(), false, true, -2.0)
        body.shadow.writer().rewind()
        body.shadow.upload()

        for (walker in walkers) {
            walker.root.vertexBuffer = body
        }

        extrudeContour(Rectangle(Vector2(-50.0, -1.0), 100.0, 1.0).contour, 100.0, 0.0, ground.shadow.writer(), false, true, -50.0)
        ground.shadow.writer().rewind()
        ground.shadow.upload()
    }

    private fun drawNode(node: SceneNode) {
        drawer.pushModel()
        drawer.model *= node.transform
        drawer.model *= node.joint.matrix

        node.vertexBuffer?.let {
                drawer.vertexBuffer(it, DrawPrimitive.TRIANGLES)
        }
        for (child in node.children) {
            drawNode(child)
        }
        drawer.popModel()
    }

    override fun draw() {
        drawer.drawStyle.depthWrite = true
        drawer.drawStyle.depthTestPass = DepthTestPass.LESS_OR_EQUAL
        drawer.background(ColorRGBa.BLACK)

        drawer.perspective(90.0, width.toDouble() / height.toDouble(), 0.01, 100.0)

        val walker = walkers[0]

        drawer.lookAt(Vector3(walker.x, walker.y+8.0, walker.z)-walker.smoothDirection*8.0, walker.position+Vector3(walker.x*1.0, walker.y, walker.z), Vector3.UNIT_Y)

        // -- draw floor
        drawer.fill = ColorRGBa.GRAY.shade(0.25)
        drawer.isolated {
            drawer.shadeStyle = shadeStyle {
                fragmentTransform = """
                    float r = length(va_position.xz);
                    float phi = atan(va_position.x, va_position.z);
                    x_fill.rgb *= (cos(r*10.0)*0.2+0.8);
                    """
            }
            drawer.vertexBuffer(ground, DrawPrimitive.TRIANGLES)
        }
        drawer.fill = ColorRGBa.PINK

        walkers.forEach {
            it.update()
            drawer.isolated {
                drawer.shadeStyle = shadeStyle {
                    fragmentTransform = """
                    x_fill.rgb *= -v_viewNormal.y;
                    """
                }
                drawNode(it.root)
            }
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
        it.x = (it.x - it.tx).coerceIn(-0.1/2, 0.1/2) + it.tx
        it.y = (it.y - it.ty).coerceIn(-0.1/2, 0.1/2) + it.ty
        it.z = (it.z - it.tz).coerceIn(-0.1/2, 0.1/2) + it.tz
    }
}

fun optimize(endPoint: SceneNode, target: Vector3, chain:List<Joint>):Double {
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

fun main(args: Array<String>) {
    application(
            IK003(),
            configuration {
                width = 1280
                height = 720
            }
    )
}