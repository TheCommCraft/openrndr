package org.openrndr.draw


expect abstract class VertexBuffer {
    abstract val session: Session?

    abstract val vertexFormat: VertexFormat
    abstract val vertexCount: Int

    /**
     * Gives a read/write shadow for the vertex buffer
     */
    abstract val shadow: VertexBufferShadow
    /**
     * Destroy the vertex buffer
     */
    abstract fun destroy()

    fun put(elementOffset: Int = 0, putter: BufferWriter.() -> Unit): Int

    companion object {
        fun createDynamic(format: VertexFormat, vertexCount: Int, session: Session? = Session.active): VertexBuffer
        fun createFromFloats(format: VertexFormat, data: FloatArray, session: Session?): VertexBuffer
    }
}

/**
 * VertexBuffer builder function.
 * @param vertexFormat a VertexFormat object that describes the vertex layout
 * @param vertexCount the number of vertices the vertex buffer should hold
 */
fun vertexBuffer(vertexFormat: VertexFormat, vertexCount: Int, session: Session? = Session.active): VertexBuffer {
    val vertexBuffer = VertexBuffer.createDynamic(vertexFormat, vertexCount, session)
    return vertexBuffer
}