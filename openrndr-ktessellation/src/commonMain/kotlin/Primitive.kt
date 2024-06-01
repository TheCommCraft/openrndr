package org.openrndr.ktessellation

import org.openrndr.math.Vector2

data class Primitive(
    /**
     * The OpenGL constant defining the type of this primitive
     *
     */
    val type: Int
) {
    /**
     * A list of the indices of the vertices required to draw this primitive.
     *
     */
    val positions: MutableList<Vector2> = mutableListOf()
}