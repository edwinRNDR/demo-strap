package post

import org.openrndr.draw.Filter
import org.openrndr.filter.filterShaderFromUrl
import org.openrndr.resourceUrl

class FloorCoc : Filter(filterShaderFromUrl(resourceUrl("/shaders/floor-coc.frag"))) {


}