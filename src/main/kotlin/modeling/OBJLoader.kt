package modeling

import lzma.sdk.lzma.Decoder
import lzma.streams.LzmaInputStream
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import java.io.BufferedInputStream
import java.net.URL

class Triangle(val positions: Array<Vector3>, val normals: Array<Vector3>) {
    fun transform(t: Matrix44): Triangle {
        return Triangle(positions.map { (t * it.xyz1).div }.toTypedArray(), normals)
    }
}

class Box(val corner: Vector3, val width: Double, val height: Double, val depth: Double)

fun bounds(triangles: List<Triangle>): Box {
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var minZ = Double.POSITIVE_INFINITY

    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    var maxZ = Double.NEGATIVE_INFINITY

    triangles.forEach {
        it.positions.forEach {
            minX = Math.min(minX, it.x)
            minY = Math.min(minY, it.y)
            minZ = Math.min(minZ, it.z)

            maxX = Math.max(maxX, it.x)
            maxY = Math.max(maxY, it.y)
            maxZ = Math.max(maxZ, it.z)
        }
    }
    return Box(Vector3(minX, minY, minZ), maxX - minX, maxY - minY, maxZ - minZ)
}

fun loadOBJfromLZMA(url: URL): Map<String, List<Triangle>>  {
    url.openStream().use {
        val compressedIn = LzmaInputStream(
                BufferedInputStream(it),
                Decoder())

        return loadOBJ(compressedIn.reader().readLines().toList())
    }
}

fun loadOBJ(lines: List<String> ): Map<String, List<Triangle>> {
    val meshes = mutableMapOf<String, List<Triangle>>()
    val positions = mutableListOf<Vector3>()
    val normals = mutableListOf<Vector3>()
    var activeMesh = mutableListOf<Triangle>()

    lines.forEach { line ->
        if (line.isNotEmpty()) {
            val tokens = line.split(Regex("[ |\t]+")).map { it.trim() }.filter { it.isNotEmpty() }

            if (tokens.isNotEmpty())
                when (tokens[0]) {
                    "v" -> {
                        positions += Vector3(tokens[1].toDouble(), tokens[2].toDouble(), tokens[3].toDouble())
                    }
                    "vn" -> normals += Vector3(tokens[1].toDouble(), tokens[2].toDouble(), tokens[3].toDouble())
                    "g" -> {
                        activeMesh = mutableListOf<Triangle>()
                        meshes[tokens[1]] = activeMesh
                    }
                    "f" -> {
                        val indices = tokens.subList(1, tokens.size).map { it.split("/") }.map {
                            it.map { it.toIntOrNull() }
                        }

                        if (indices.size == 3) {
                            val ps = arrayOf(
                                    indices[0][0]?.let { positions[it - 1] } ?: Vector3.ZERO,
                                    indices[1][0]?.let { positions[it - 1] } ?: Vector3.ZERO,
                                    indices[2][0]?.let { positions[it - 1] } ?: Vector3.ZERO)

                            val ns = arrayOf(
                                    indices[0][2]?.let { normals[it - 1] } ?: Vector3.ZERO,
                                    indices[1][2]?.let { normals[it - 1] } ?: Vector3.ZERO,
                                    indices[2][2]?.let { normals[it - 1] } ?: Vector3.ZERO)

                            activeMesh.add(Triangle(ps, ns))
                        } else {
                            TODO("implement non triangular surfaces ${indices.size}")
                        }
                    }
                }
        }
    }
    return meshes
}