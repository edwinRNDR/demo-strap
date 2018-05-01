package sketches.ik002

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
import org.openrndr.math.transforms.transform
import org.openrndr.math.transforms.translate
import org.openrndr.shape.Rectangle

class Joint(val minX: Double = -Math.PI / 4, val maxX: Double = Math.PI / 4, val minY: Double = 0.0, val maxY: Double =0.0, val minZ: Double = -Math.PI / 4, val maxZ: Double = Math.PI / 4) {
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

        animate(::footY, position.y+.5, 250, Easing.CubicInOut)
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

    var x: Double = 0.0
    var y: Double = 3.5
    var z: Double = 0.0
    var position = Vector3(0.0, 0.0, 0.0)
    val legs = mutableListOf<Leg>()

    var smoothDirection = Vector3(0.0, 0.0, 1.0)
    var direction = Vector3(0.0, 0.0, 1.0)
    lateinit var leg: VertexBuffer

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

    fun addLeg(offset: Vector3) {
        var current = root
        for (i in 0..2) {
            val n = SceneNode(translate(Vector3(0.0, -1.0, 0.0) + if (i == 0) offset else Vector3.ZERO))
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
//            legs[(legIndex+2)%4].apply {
//
//                val position= Vector3(x, y, z)
//                stepTo((position + basis*(offset*1.5) + direction*2.0).copy(y=0.0))
//
//            }

            legIndex = (legIndex+1)

            if (Math.random() < 0.5) {
                direction = rotate(Vector3.UNIT_Y, 2.0) * direction
            }
        }

        smoothDirection = (smoothDirection * 0.9 + direction *0.1).normalized
        val smoothRight = smoothDirection.cross(Vector3.UNIT_Y)

        val sm = Matrix44.fromColumnVectors(smoothRight.xyz0, Vector3.UNIT_Y.xyz0, smoothDirection.xyz0.normalized, Vector4.UNIT_W)

        root.transform =
                transform {
                    translate(position + Vector3(x, y, z))

                    multiply(sm)
                    rotate(Vector3.UNIT_X,  Math.cos(x)*10.0)

                }
        for (j in 0 until endPoints.size) {



            push(endPoints[j])
            for (i in 0 until 100) {
                optimize(endPoints[j], legs[j].position)
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

class IK002 : Program() {
    lateinit var body: VertexBuffer

    lateinit var ground: VertexBuffer

    lateinit var walker: Walker

    override fun setup() {
        val format = vertexFormat {
            position(3)
            normal(3)
            attribute("id", 1, VertexElementType.FLOAT32)
            attribute("center", 3, VertexElementType.FLOAT32)
        }


        walker = Walker()

        ground = vertexBuffer(format, 100)
        body = vertexBuffer(format, 100)

        extrudeContour(Rectangle(Vector2(-2.0, -1.5), 4.0, 1.0).contour, 4.0, 0.0, body.shadow.writer(), false, true, -2.0)
        body.shadow.writer().rewind()
        body.shadow.upload()



        walker.root.vertexBuffer = body

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
        //drawer.vertexBuffer(leg, DrawPrimitive.TRIANGLES)
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
        drawer.lookAt(Vector3(walker.x, walker.y+8.0, walker.z + 8.0), walker.position+Vector3(walker.x*1.0, walker.y, walker.z), Vector3.UNIT_Y)


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

        walker.update()




        drawer.isolated {
            drawer.shadeStyle = shadeStyle {
                fragmentTransform = """
                    x_fill.rgb *= -v_viewNormal.y;
                    """
            }
            drawNode(walker.root)
        }

//        for (target in walker.legs) {
//            drawer.isolated {
//                drawer.translate(target.position)
//                drawer.vertexBuffer(walker.leg, DrawPrimitive.TRIANGLES)
//            }
//        }
//
//        for (target in walker.endPoints) {
//
//            drawer.fill = ColorRGBa.RED
//            val position = forward(target)
//            drawer.isolated {
//                drawer.translate(position)
//                drawer.vertexBuffer(walker.leg, DrawPrimitive.TRIANGLES)
//            }
//        }


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



fun optimize(endPoint: SceneNode, target: Vector3) {
    val currentError = (forward(endPoint) - target).length
    val chain = endPoint.ikChain.map { it.joint }
    val delta = 0.01
    val l = 0.001

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

//        joint.x *= 0.999
//        joint.y *= 0.999
//        joint.z *= 0.999
    }
}


fun main(args: Array<String>) {
    application(
            IK002(),
            configuration {
                width = 1280
                height = 720
            }
    )
}