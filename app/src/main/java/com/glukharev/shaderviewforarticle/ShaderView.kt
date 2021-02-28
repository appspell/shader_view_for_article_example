package com.glukharev.shaderviewforarticle

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

private const val OPENGL_VERSION = 3

private const val BUFFER_BIT_PER_CHANNEL = 8
private const val BUFFER_BIT_DEPTH = 16
private const val BUFFER_BIT_STENCIL = 0

class ShaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    GLTextureView(context, attrs, defStyleAttr) {

    init {
        // define OpenGL version
        setEGLContextClientVersion(OPENGL_VERSION)

        // set RGBA_8888 to support transparency
        setEGLConfigChooser(
            BUFFER_BIT_PER_CHANNEL,
            BUFFER_BIT_PER_CHANNEL,
            BUFFER_BIT_PER_CHANNEL,
            BUFFER_BIT_PER_CHANNEL,
            BUFFER_BIT_DEPTH,
            BUFFER_BIT_STENCIL
        )

        // load shader's source code from RAW files
        val vsh = context.resources.getRawTextFile(R.raw.vertex_shader)
        val fsh = context.resources.getRawTextFile(R.raw.fragment_shader)

        // set renderer
        setRenderer(QuadRender(vertexShaderSource = vsh, fragmentShaderSource = fsh))

        // set render mode RENDERMODE_WHEN_DIRTY or RENDERMODE_CONTINUOUSLY
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY) // or GLSurfaceView.RENDERMODE_CONTINUOUSLY if we need to update it each frame
    }
}