package sketches

import modeling.extrudeContour
import org.openrndr.Program
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.configuration
import org.openrndr.draw.*
import org.openrndr.math.*
import org.openrndr.math.transforms.rotate
import org.openrndr.math.transforms.translate
import org.openrndr.shape.Rectangle

private class Joint(val minX: Double = -Math.PI / 4, val maxX: Double = Math.PI / 4, val minY: Double = -Math.PI / 4, val maxY: Double = Math.PI / 4, val minZ: Double = -Math.PI / 4, val maxZ: Double = Math.PI / 4) {
    var x: Double = 0.0
    var y: Double = 0.0
    var z: Double = 0.0

    val matrix
        get() = Quaternion.fromAnglesRadian(x, y, z).matrix
}

private class SceneNode(var transform: Matrix44 = Matrix44.IDENTITY, val joint: Joint = Joint()) {
    val children = mutableListOf<SceneNode>()
    var parent: SceneNode? = null

    val root: SceneNode?
        get() = if (parent == null) this else parent?.root

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

private fun forward(node: SceneNode): Vector3 {
    val chain = node.ikChain.reversed()
    var transform = Matrix44.IDENTITY
    chain.forEach {
        transform *= it.transform
        transform *= it.joint.matrix
    }
    return (transform * Vector4(0.0, 0.0, 1.0, 1.0)).xyz
}

class IK001 : Program() {
    lateinit var body: VertexBuffer
    private lateinit var endPoint: SceneNode
    private val root = SceneNode(joint = Joint(0.0, 0.0, 0.0, 0.0, 0.0, 0.0))

    override fun setup() {
        val format = vertexFormat {
            position(3)
            normal(3)
            attribute("id", 1, VertexElementType.FLOAT32)
            attribute("center", 3, VertexElementType.FLOAT32)
        }

        body = vertexBuffer(format, 100)
        extrudeContour(Rectangle(Vector2(-0.1, -0.1), 0.2, 0.2).contour, 1.0, 0.0, body.shadow.writer(), false, true)
        body.shadow.writer().rewind()
        body.shadow.upload()

        var current = root
        for (i in 0..45) {
            val n = SceneNode(translate(0.0, 0.0, 1.0))
            current.children.add(n)
            n.parent = current
            current = n
            endPoint = n
        }
    }

    private fun drawNode(node: SceneNode) {
        drawer.pushModel()
        drawer.model *= node.transform
        drawer.model *= node.joint.matrix
        drawer.vertexBuffer(body, DrawPrimitive.TRIANGLES)
        for (child in node.children) {
            drawNode(child)
        }
        drawer.popModel()
    }

    fun optimize(target: Vector3) {
        val currentError = (forward(endPoint) - target).length
        val chain = endPoint.ikChain.map { it.joint }
        val delta = 0.005
        val l = 0.00005

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
        }
    }

    fun relax(target: Vector3) {
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

            joint.x *= 0.999
            joint.y *= 0.999
            joint.z *= 0.999
        }
    }

    override fun draw() {
        drawer.background(ColorRGBa.BLACK)
        drawer.fill = ColorRGBa.PINK
        drawer.perspective(90.0, width.toDouble() / height.toDouble(), 0.01, 100.0)
        drawer.lookAt(Vector3(8.0, 8.0, 8.0), Vector3.ZERO, Vector3.UNIT_Z)

        root.transform = rotate(Vector3.UNIT_Y, seconds * 30.0)

        val s = seconds
        val target = Vector3(Math.cos(s * 2.0) * 12.0, Math.sin(s * 0.43254) * 12.0, Math.cos(s * 1.32) * 12.0)

        for (i in 0 until 10) {
            optimize(target)
            relax(target)
        }

        drawer.isolated {
            drawNode(root)
        }

        drawer.translate(target)
        drawer.vertexBuffer(body, DrawPrimitive.TRIANGLES)

    }
}

fun main(args: Array<String>) {
    application(
            IK001(),
            configuration {
                width = 1280
                height = 720
            }
    )
}