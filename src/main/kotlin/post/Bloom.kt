package post

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