package org.openrndr.draw

import org.openrndr.internal.Driver
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.normalMatrix

private var lastModel = Matrix44.IDENTITY
private var lastModelNormal = Matrix44.IDENTITY
private var lastView = Matrix44.IDENTITY
private var lastViewNormal = Matrix44.IDENTITY

var contextBlocks = mutableMapOf<Long, UniformBlock?>()
expect val useContextBlock : Boolean


@Suppress("MemberVisibilityCanPrivate")
data class DrawContext(val model: Matrix44, val view: Matrix44, val projection: Matrix44, val width: Int, val height: Int, val contentScale: Double, val modelViewScalingFactor:Double) {
    fun applyToShader(shader: Shader) {
        if (!useContextBlock) {
            if (shader.hasUniform("u_viewMatrix")) {
                shader.uniform("u_viewMatrix", view)
            }
            if (shader.hasUniform("u_modelMatrix")) {
                shader.uniform("u_modelMatrix", model)
            }
            if (shader.hasUniform("u_projectionMatrix")) {
                shader.uniform("u_projectionMatrix", projection)
            }
            if (shader.hasUniform("u_viewDimensions")) {
                shader.uniform("u_viewDimensions", Vector2(width.toDouble(), height.toDouble()))
            }
            if (shader.hasUniform("u_modelNormalMatrix")) {
                val normalMatrix = if (model === lastModel) lastModelNormal else {
                    lastModelNormal = if (model !== Matrix44.IDENTITY) normalMatrix(model) else Matrix44.IDENTITY
                    lastModel = model
                    lastModelNormal
                }
                shader.uniform("u_modelNormalMatrix", normalMatrix)
            }
            if (shader.hasUniform("u_viewNormalMatrix")) {
                val normalMatrix = if (view === lastView) lastViewNormal else {
                    lastViewNormal = if (view !== Matrix44.IDENTITY) normalMatrix(view) else Matrix44.IDENTITY
                    lastView = view
                    lastViewNormal
                }
                shader.uniform("u_viewNormalMatrix", normalMatrix)
            }
            if (shader.hasUniform("u_contentScale")) {
                shader.uniform("u_contentScale", contentScale)
            }

            if (shader.hasUniform("u_modelViewScalingFactor")) {
                shader.uniform("u_modelViewScalingFactor", modelViewScalingFactor)
            }

        } else {
            val contextBlock = contextBlocks.getOrPut(Driver.instance.contextID) {
                shader.createBlock("ContextBlock")
            }

            contextBlock?.apply {
                uniform("u_viewMatrix", view)
                uniform("u_modelMatrix", model)
                uniform("u_projectionMatrix", projection)
                uniform("u_viewDimensions", Vector2(width.toDouble(), height.toDouble()))
                run {
                    val normalMatrix = if (model === lastModel) lastModelNormal else {
                        lastModelNormal = if (model !== Matrix44.IDENTITY) normalMatrix(model) else Matrix44.IDENTITY
                        lastModel = model
                        lastModelNormal
                    }
                    uniform("u_modelNormalMatrix", normalMatrix)
                }
                run {
                    val normalMatrix = if (view === lastView) lastViewNormal else {
                        lastViewNormal = if (view !== Matrix44.IDENTITY) normalMatrix(view) else Matrix44.IDENTITY
                        lastView = view
                        lastViewNormal
                    }
                    uniform("u_viewNormalMatrix", normalMatrix)
                }
                uniform("u_contentScale", contentScale.toFloat())
                uniform("u_modelViewScalingFactor", modelViewScalingFactor.toFloat())
                if (dirty) {
                    upload()
                }
                shader.block("ContextBlock", this)
            }
        }
    }
}
