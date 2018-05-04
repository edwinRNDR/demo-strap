package post

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Filter
import org.openrndr.filter.filterShaderFromUrl
import org.openrndr.resourceUrl

class BloomDownscale : Filter(filterShaderFromUrl(resourceUrl("/shaders/bloom-downscale.frag"))) {

}
class BloomUpscale : Filter(filterShaderFromUrl(resourceUrl("/shaders/bloom-upscale.frag"))) {

    var gain:Double by parameters
    var shape:Double by parameters
    var seed:Double by parameters

    init {
        gain = 1.0
        shape = 1.0
        seed = 1.0
    }
}

class BloomCombine: Filter(filterShaderFromUrl(resourceUrl("/shaders/bloom-combine.frag"))) {

    var gain: Double by parameters
    var bias: ColorRGBa by parameters

    init {
        bias = ColorRGBa.BLACK
        gain = 1.0
    }
}

class FlareCombine: Filter(filterShaderFromUrl(resourceUrl("/shaders/flare-combine.frag"))) {

    var gain: Double by parameters
    var bias: ColorRGBa by parameters
    var time: Double by parameters

    init {
        bias = ColorRGBa.BLACK
        gain = 1.0
    }
}


class FlareGhost: Filter(filterShaderFromUrl(resourceUrl("/shaders/flare-ghost.frag"))) {
    var ghosts:Int by parameters
    var haloRadius:Double by parameters
    var dispersal:Double by parameters

    init {
        ghosts = 5
        haloRadius = 0.25
        dispersal = 0.367
    }
}