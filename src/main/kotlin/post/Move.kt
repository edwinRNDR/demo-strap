package studio.rndr.post

import org.openrndr.draw.Filter
import org.openrndr.filter.filterShaderFromUrl
import org.openrndr.resourceUrl

class Move : Filter(filterShaderFromUrl(resourceUrl("/shaders/move.frag"))) {

    var velocity: Int by parameters
    var lap0: Int by parameters
    var lap1: Int by parameters
    var real: Int by parameters
    var threshold: Double by parameters

    var time: Double by parameters

    init {
        velocity = 1
        lap0 = 2
        lap1 = 3
        real = 4
        threshold = 1.0
    }
}