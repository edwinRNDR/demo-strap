package modeling


import org.openrndr.draw.BufferWriter
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.Triangulator

fun extrudeContour(contour: ShapeContour, extrusion: Double, objectId:Double, writer: BufferWriter,  flipNormals:Boolean=false, generateBottom:Boolean=true, zBase:Double =0.0) {
    val linear = contour.sampleLinear(2.0)
    val z = Vector3(0.0, 0.0, zBase)
    linear.segments.forEach {
        writer.apply {
            val n = (it.end - it.start).normalized.perpendicular.xy0 * if (flipNormals) -1.0 else 1.0

            write(it.start.xy0 + z, n)
            write(objectId.toFloat())
            write(Vector3.ZERO)

            write(it.end.xy0 + z, n)
            write(objectId.toFloat())
            write(Vector3.ZERO)


            write(it.end.vector3(z = extrusion) + z, n)
            write(objectId.toFloat())
            write(Vector3.UNIT_Z)


            write(it.end.vector3(z = extrusion) + z, n)
            write(objectId.toFloat())
            write(Vector3.UNIT_Z)


            write(it.start.vector3(z = extrusion) + z, n)
            write(objectId.toFloat())
            write(Vector3.UNIT_Z)

            write(it.start.xy0 + z, n)
            write(objectId.toFloat())
            write(Vector3.ZERO)

        }
    }
    Triangulator().triangulate(linear).let { tris ->

        if (generateBottom) {
            tris.forEach {
                writer.write(it.xy0+z, Vector3.UNIT_Z * if (flipNormals) -1.0 else 1.0)
                writer.write(objectId.toFloat())
                writer.write(Vector3.ZERO)
            }
        }
        tris.forEach {
            writer.write(it.vector3(z = extrusion)+z, Vector3.UNIT_Z * -1.0 * if (flipNormals) -1.0 else 1.0)
            writer.write(objectId.toFloat())
            writer.write(Vector3.UNIT_Z)
        }
    }
}

fun extrudeContourUV(contour: ShapeContour, extrusion: Double, objectId:Double, writer: BufferWriter,  flipNormals:Boolean=false, generateBottom:Boolean=true, zBase:Double =0.0) {
    val linear = contour.sampleLinear(2.0)
    val length = linear.length
    val z = Vector3(0.0, 0.0, zBase)

    var uOffset = 0.0
    linear.segments.forEach {
        writer.apply {
            val n = (it.end - it.start).normalized.perpendicular.xy0 * if (flipNormals) -1.0 else 1.0

            val u0 = uOffset/length
            val u1 = u0 + (it.end-it.start).length/length

            write(it.start.xy0 + z, n)
            write(objectId.toFloat())
            write(Vector3.ZERO)
            write(Vector2(u0, 0.0))

            write(it.end.xy0 + z, n)
            write(objectId.toFloat())
            write(Vector3.ZERO)
            write(Vector2(u1, 0.0))

            write(it.end.vector3(z = extrusion) + z, n)
            write(objectId.toFloat())
            write(Vector3.UNIT_Z)
            write(Vector2(u1, 1.0))

            write(it.end.vector3(z = extrusion) + z, n)
            write(objectId.toFloat())
            write(Vector3.UNIT_Z)
            write(Vector2(u1, 1.0))

            write(it.start.vector3(z = extrusion) + z, n)
            write(objectId.toFloat())
            write(Vector3.UNIT_Z)
            write(Vector2(u0, 1.0))

            write(it.start.xy0 + z, n)
            write(objectId.toFloat())
            write(Vector3.ZERO)
            write(Vector2(u0, 0.0))

            uOffset += (it.end-it.start).length
        }
    }
    Triangulator().triangulate(linear).let { tris ->

        if (generateBottom) {
            tris.forEach {
                writer.write(it.xy0+z, Vector3.UNIT_Z * if (flipNormals) -1.0 else 1.0)
                writer.write(objectId.toFloat())
                writer.write(Vector3.ZERO)
                writer.write(Vector2.ZERO)
            }
        }
        tris.forEach {
            writer.write(it.vector3(z = extrusion)+z, Vector3.UNIT_Z * -1.0 * if (flipNormals) -1.0 else 1.0)
            writer.write(objectId.toFloat())
            writer.write(Vector3.UNIT_Z)
            writer.write(Vector2.ZERO)
        }
    }
}
