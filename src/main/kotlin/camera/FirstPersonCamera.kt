package studio.rndr.camera

import org.openrndr.KeyboardModifier
import org.openrndr.Program
import org.openrndr.math.*
import org.openrndr.math.transforms.translate

class FirstPersonCamera {

    var viewMatrix = Matrix44.IDENTITY
    var cameraQuat = Quaternion.IDENTITY

    var position = Vector3(0.0, 1.0, 0.0)
    var keyPitch = 0.0
    var keyYaw = 0.0
    var keyRoll = 0.0

    var fov = 90.0
    var aperture = 1.0
    var focalPlane = 6.71
    var exposure = 1.0
    var centerX = 0.0
    var centerY = 0.0

    var lastMousePosition = Vector2(0.0, 0.0)

    fun fromFrame(frame:CameraKey):FirstPersonCamera {
        position = frame.position
        cameraQuat = frame.orientation
        fov = frame.fov
        aperture = frame.aperture
        focalPlane = frame.focalPlane
        exposure = frame.exposure
        centerX = frame.centerX
        centerY = frame.centerY
        update()

        return this
    }

    val forward:Vector3 get() {
        val mat = cameraQuat.matrix.transposed
        return mat[2].xyz
    }
    val up:Vector3 get() {
        val mat = cameraQuat.matrix.transposed
        return mat[1].xyz
    }
    val right:Vector3 get() {
        val mat = cameraQuat.matrix.transposed
        return mat[0].xyz
    }

    fun mouseScrolled(event:Program.Mouse.MouseEvent) {
        val mat = cameraQuat.matrix.transposed
        val foward = mat[2].xyz
        val strafe = mat[0].xyz
        val up = mat[1].xyz


        if (KeyboardModifier.CTRL in event.modifiers && KeyboardModifier.SHIFT !in event.modifiers) {
            aperture += event.rotation.y*0.1
            exposure += event.rotation.x*0.05

        }
        else if (KeyboardModifier.CTRL in event.modifiers && KeyboardModifier.SHIFT in event.modifiers) {
            focalPlane += event.rotation.y/10.0
            println("focal plane: $focalPlane")
        }
        else
        if (KeyboardModifier.ALT in event.modifiers && KeyboardModifier.SHIFT !in event.modifiers) {
            fov += event.rotation.y

        } else if (KeyboardModifier.ALT in event.modifiers && KeyboardModifier.SHIFT in event.modifiers) {
            centerX += event.rotation.x * 0.1
            centerY += event.rotation.y * 0.1
        } else {
            if (KeyboardModifier.SHIFT in event.modifiers) {
                position += up * event.rotation.y * 1.0
            } else {
                position += foward * event.rotation.y * 1.0
            }
            position += strafe * event.rotation.x * 1.0
            println(position)
        }
        update()
    }

    fun mouseMoved(event: Program.Mouse.MouseEvent) {

        val delta = event.position - lastMousePosition

        if (event.modifiers.contains(KeyboardModifier.SHIFT)) {
            if (event.modifiers.contains(KeyboardModifier.ALT))

                keyYaw = delta.x * 0.01
            else {
                keyPitch = delta.x * 0.01
                keyRoll = delta.y * 0.01
            }


            update()
        }
        lastMousePosition = event.position
    }

    fun setup(program:Program) {
        program.mouse.scrolled.listen { mouseScrolled(it) }
        program.mouse.moved.listen { mouseMoved(it) }

    }

    fun update() {
        val keyQuat = Quaternion.fromAnglesRadian(keyPitch, keyRoll, keyYaw)
        cameraQuat = keyQuat * cameraQuat
        keyPitch = 0.0
        keyYaw = 0.0
        keyRoll = 0.0

        viewMatrix = cameraQuat.matrix * translate(position*-1.0)
    }

}