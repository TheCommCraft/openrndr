package org.openrndr.internal

import org.openrndr.draw.*
import org.openrndr.math.Vector2
import kotlin.math.floor

class GlyphRectangle(val character: Char, val x: Double, val y: Double, val width: Double, val height: Double)

class FontImageMapDrawer {

    private val shaderManager: ShadeStyleManager = ShadeStyleManager.fromGenerators(
        "font-image-map",
        vsGenerator = Driver.instance.shaderGenerators::fontImageMapVertexShader,
        fsGenerator = Driver.instance.shaderGenerators::fontImageMapFragmentShader
    )

    private val maxQuads = 20_000

    private val vertexFormat = VertexFormat().apply {
        textureCoordinate(2)
        attribute("bounds", VertexElementType.VECTOR4_FLOAT32)
        position(3)
        attribute("instance", VertexElementType.FLOAT32)
    }

    private val fewQuads = List(DrawerConfiguration.vertexBufferMultiBufferCount) {
        vertexBuffer(vertexFormat, 6 * 128)
    }

    private val manyQuads = VertexBuffer.createDynamic(vertexFormat, 6 * maxQuads)

    private var quadCount = 0

    var counter = 0

    fun getQueue(size: Int): VertexBuffer {
        if (size < 128) {
            return fewQuads[counter.mod(fewQuads.size)]
        } else {
            return manyQuads
        }
    }

    fun drawText(
        context: DrawContext,
        drawStyle: DrawStyle,
        text: String,
        x: Double,
        y: Double
    ) = drawTexts(context, drawStyle, listOf(text), listOf(Vector2(x, y)))


    fun drawTexts(
        context: DrawContext,
        drawStyle: DrawStyle,
        texts: List<String>,
        positions: List<Vector2>
    ): List<List<GlyphRectangle>> {
        val vertices = if (texts.sumOf { it.length } < 128) fewQuads[counter.mod(fewQuads.size)] else manyQuads

        val fontMap = drawStyle.fontMap as? FontImageMap
        if (fontMap != null) {
            var instance = 0

            val textAndPositionPairs = texts.zip(positions)
            for ((text, position) in textAndPositionPairs) {
                var cursorX = 0.0
                val cursorY = 0.0

                val bw = vertices.shadow.writer()
                bw.position = vertices.vertexFormat.size * quadCount * 6

                var lastChar: Char? = null
                text.forEach {
                    val lc = lastChar
                    if (drawStyle.kerning == KernMode.METRIC) {
                        cursorX += if (lc != null) fontMap.kerning(lc, it) else 0.0
                    }
                    val glyphMetrics = fontMap.glyphMetrics[it] ?: fontMap.glyphMetrics.getValue(' ')
                    val (dx, _) = insertCharacterQuad(
                        fontMap,
                        bw,
                        it,
                        position.x + cursorX,
                        position.y + cursorY,
                        instance,
                        drawStyle.textSetting
                    )
                    cursorX += glyphMetrics.advanceWidth + dx
                    lastChar = it
                }
                instance++
            }
            flush(context, drawStyle, vertices)
        }
        return emptyList()
    }

    var queuedInstances = 0
    fun queueText(
        fontMap: FontMap,
        text: String,
        x: Double,
        y: Double,
        tracking: Double = 0.0,
        kerning: KernMode, // = KernMode.METRIC,
        textSetting: TextSettingMode,// = TextSettingMode.PIXEL,
        vertices: VertexBuffer,
    ) {
        val bw = vertices.shadow.writer()
        bw.position = vertices.vertexFormat.size * quadCount * 6
        fontMap as FontImageMap
        var cursorX = 0.0
        val cursorY = 0.0
        var lastChar: Char? = null
        text.forEach {
            val lc = lastChar
            val metrics = fontMap.glyphMetrics[it]
            metrics?.let { m ->
                if (kerning == KernMode.METRIC) {
                    cursorX += if (lc != null) fontMap.kerning(lc, it) else 0.0
                }
                val (dx, _) = insertCharacterQuad(
                    fontMap,
                    bw,
                    it,
                    x + cursorX,
                    y + cursorY,
                    0,
                    textSetting
                )
                cursorX += m.advanceWidth + tracking + dx
                lastChar = it
            }
        }
        queuedInstances++
    }


    fun flush(context: DrawContext, drawStyle: DrawStyle, vertices: VertexBuffer) {
        if (quadCount > 0) {
            vertices.shadow.uploadElements(0, quadCount * 6)
            val shader = shaderManager.shader(drawStyle.shadeStyle, vertices.vertexFormat)
            shader.begin()
            context.applyToShader(shader)

            Driver.instance.setState(drawStyle)
            drawStyle.applyToShader(shader)
            (drawStyle.fontMap as FontImageMap).texture.bind(0)
            Driver.instance.drawVertexBuffer(
                shader,
                listOf(vertices),
                DrawPrimitive.TRIANGLES,
                0,
                quadCount * 6,
                verticesPerPatch = 0
            )
            shader.end()
            quadCount = 0
        }
        queuedInstances = 0
        if (vertices != manyQuads) {
            counter++
        }
    }

    private fun insertCharacterQuad(
        fontMap: FontImageMap,
        bw: BufferWriter,
        character: Char,
        cx: Double,
        cy: Double,
        instance: Int,
        textSetting: TextSettingMode
    ): Pair<Double, GlyphRectangle?> {
        val rectangle = fontMap.map[character] ?: fontMap.map[' ']
        val targetContentScale = RenderTarget.active.contentScale
        val fmcs = fontMap.contentScale.toFloat()

        val metrics =
            fontMap.glyphMetrics[character] ?: fontMap.glyphMetrics[' '] ?: error("glyph or space substitute not found")
        val xshift = (metrics.xBitmapShift / fmcs).toFloat()
        val yshift = (metrics.yBitmapShift / fmcs).toFloat()


        val sx = cx + xshift
        val sy = cy + yshift

        val x = if (textSetting == TextSettingMode.PIXEL) floor(sx * targetContentScale) / targetContentScale else sx
        val y = if (textSetting == TextSettingMode.PIXEL) floor(sy * targetContentScale) / targetContentScale else sy


        val glyphRectangle =
            if (rectangle != null) {
                val pad = 2.0f
                val u0 = (rectangle.x.toFloat() - pad) / fontMap.texture.effectiveWidth
                val u1 = (rectangle.x.toFloat() + rectangle.width.toFloat() + pad) / fontMap.texture.effectiveWidth
                val v0 = (rectangle.y.toFloat() - pad) / fontMap.texture.effectiveHeight
                val v1 = v0 + (pad * 2 + rectangle.height.toFloat()) / fontMap.texture.effectiveHeight

                val x0 = x.toFloat() - pad / fmcs
                val x1 = x.toFloat() + (rectangle.width.toFloat() / fmcs) + pad / fmcs
                val y0 = y.toFloat() - pad / fmcs
                val y1 = y.toFloat() + rectangle.height.toFloat() / fmcs + pad / fmcs

                val s0 = 0.0f
                val t0 = 0.0f
                val s1 = 1.0f
                val t1 = 1.0f

                val w = (x1 - x0)
                val h = (y1 - y0)
                val z = quadCount.toFloat()

                val floatInstance = instance.toFloat()

                if (quadCount < maxQuads) {
                    bw.apply {
                        write(u0, v0); write(s0, t0, w, h); write(x0, y0, z); write(floatInstance)
                        write(u1, v0); write(s1, t0, w, h); write(x1, y0, z); write(floatInstance)
                        write(u1, v1); write(s1, t1, w, h); write(x1, y1, z); write(floatInstance)

                        write(u0, v0); write(s0, t0, w, h); write(x0, y0, z); write(floatInstance)
                        write(u0, v1); write(s0, t1, w, h); write(x0, y1, z); write(floatInstance)
                        write(u1, v1); write(s1, t1, w, h); write(x1, y1, z); write(floatInstance)
                    }
                    quadCount++
                }
                GlyphRectangle(character, x0.toDouble(), y0.toDouble(), (x1 - x0).toDouble(), (y1 - y0).toDouble())
            } else {
                null
            }
        return Pair(x - sx, glyphRectangle)
    }
}