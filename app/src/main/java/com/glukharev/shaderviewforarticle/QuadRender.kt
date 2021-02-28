package com.glukharev.shaderviewforarticle

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "QuadRender"

private const val FLOAT_SIZE_BYTES = 4 // size of Float
private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES =
    5 * FLOAT_SIZE_BYTES // 5 floats for each vertex (3 floats is a position and 2 texture coordinate)
private const val TRIANGLE_VERTICES_DATA_POS_OFFSET =
    0 // position coordinates start from the starts from the start of array of each vertex
private const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3 // texture coordinates start from 3rd float (4th and 5th float)

// vertex shader attributes
const val VERTEX_SHADER_IN_POSITION = "inPosition"
const val VERTEX_SHADER_IN_TEXTURE_COORD = "inTextureCoord"
const val VERTEX_SHADER_UNIFORM_MATRIX_MVP = "uMVPMatrix"
const val VERTEX_SHADER_UNIFORM_MATRIX_STM = "uSTMatrix"

const val FRAGMENT_SHADER_UNIFORM_TEXTURE = "uTexture"

private const val UNKNOWN_PROGRAM = -1
private const val UNKNOWN_ATTRIBUTE = -1

// TOOD it's not a good way to send all this data via constructor but for proof of concept it's ok
class QuadRender(
    private var vertexShaderSource: String, // source code of vertex shader
    private var fragmentShaderSource: String,// source code of fragment shader
    private var textureBitmap: Bitmap
) : GLTextureView.Renderer {
    private val quadVertices: FloatBuffer

    private val matrixMVP = FloatArray(16)
    private val matrixSTM = FloatArray(16)

    private var inPositionHandle = UNKNOWN_ATTRIBUTE
    private var inTextureHandle = UNKNOWN_ATTRIBUTE
    private var uMVPMatrixHandle = UNKNOWN_ATTRIBUTE
    private var uSTMatrixHandle = UNKNOWN_ATTRIBUTE

    private var uTextureId = UNKNOWN_ATTRIBUTE
    private var uTextureHandle = UNKNOWN_ATTRIBUTE

    private var program = UNKNOWN_PROGRAM

    init {
        // set array of Quad vertices
        val quadVerticesData = floatArrayOf(
            // [x,y,z, U,V]
            -1.0f, -1.0f, 0f, 0f, 1f,
            1.0f, -1.0f, 0f, 1f, 1f,
            -1.0f, 1.0f, 0f, 0f, 0f,
            1.0f, 1.0f, 0f, 1f, 0f
        )

        quadVertices = ByteBuffer
            .allocateDirect(quadVerticesData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quadVerticesData).position(0)
            }

        // initialize matrix
        Matrix.setIdentityM(matrixSTM, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // create shader program from the source code
        createProgram(vertexShaderSource, fragmentShaderSource)

        // bind vector shader attributes
        inPositionHandle = GLES20.glGetAttribLocation(program, VERTEX_SHADER_IN_POSITION)
        checkGlError("glGetAttribLocation $VERTEX_SHADER_IN_POSITION")
        if (inPositionHandle == UNKNOWN_ATTRIBUTE) throw RuntimeException("Could not get attrib location for $VERTEX_SHADER_IN_POSITION")

        inTextureHandle = GLES20.glGetAttribLocation(program, VERTEX_SHADER_IN_TEXTURE_COORD)
        checkGlError("glGetAttribLocation $VERTEX_SHADER_IN_TEXTURE_COORD")
        if (inTextureHandle == UNKNOWN_ATTRIBUTE) throw RuntimeException("Could not get attrib location for $VERTEX_SHADER_IN_TEXTURE_COORD")

        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, VERTEX_SHADER_UNIFORM_MATRIX_MVP)
        checkGlError("glGetUniformLocation $VERTEX_SHADER_UNIFORM_MATRIX_MVP")
        if (uMVPMatrixHandle == UNKNOWN_ATTRIBUTE) throw RuntimeException("Could not get uniform location for $VERTEX_SHADER_UNIFORM_MATRIX_MVP")

        uSTMatrixHandle = GLES20.glGetUniformLocation(program, VERTEX_SHADER_UNIFORM_MATRIX_STM)
        checkGlError("glGetUniformLocation $VERTEX_SHADER_UNIFORM_MATRIX_STM")
        if (uSTMatrixHandle == UNKNOWN_ATTRIBUTE) throw RuntimeException("Could not get uniform location for $VERTEX_SHADER_UNIFORM_MATRIX_STM")

        // (!) bind attributes for fragment shader

        // upload bitmap to GPU
        uTextureHandle = GLES30.glGetUniformLocation(program, FRAGMENT_SHADER_UNIFORM_TEXTURE)
        uTextureId = textureBitmap.toGlTexture(needToRecycle = true, GLES30.GL_TEXTURE0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // clear the our "screen"
        GLES30.glClearColor(1.0f, 1.0f, 1.0f, 0.0f)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT or GLES30.GL_COLOR_BUFFER_BIT)

        // use program
        GLES30.glUseProgram(program)

        // set shader input (built-in attributes)
        setAttribute(inPositionHandle, VERTEX_SHADER_IN_POSITION, 3, TRIANGLE_VERTICES_DATA_POS_OFFSET) // 3 because 3 floats for position
        setAttribute(inTextureHandle, VERTEX_SHADER_IN_TEXTURE_COORD, 2, TRIANGLE_VERTICES_DATA_UV_OFFSET) // 2 because 2 floats for texture coordinates

        // update matrix
        Matrix.setIdentityM(matrixMVP, 0)
        GLES30.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, matrixMVP, 0)
        GLES30.glUniformMatrix4fv(uSTMatrixHandle, 1, false, matrixSTM, 0)

        // (!) update uniforms for fragment shader
        GLES30.glUniform1i(uTextureHandle, GLES30.GL_TEXTURE0.convertTextureSlotToIndex()) // convertTextureSlotToIndex returns 0 as far as it's slot number 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0) // same texture slot which we've used on initialization
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, uTextureId)

        // activate blending for textures (to support transparency)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnable(GLES20.GL_BLEND)

        // draw our quad
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        GLES30.glFinish()
    }

    /**
     * Create shader program from source code of vertex and fragment shader
     */
    private fun createProgram(vertexSource: String, fragmentSource: String): Boolean {
        if (program != UNKNOWN_PROGRAM) {
            // delete program
            GLES30.glDeleteProgram(program)
            program = UNKNOWN_PROGRAM
        }
        // load vertex shader
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == UNKNOWN_PROGRAM) {
            return false
        }
        // load pixel shader
        val pixelShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == UNKNOWN_PROGRAM) {
            return false
        }
        program = GLES30.glCreateProgram()
        if (program != UNKNOWN_PROGRAM) {
            GLES30.glAttachShader(program, vertexShader)
            checkGlError("glAttachShader: vertex")
            GLES30.glAttachShader(program, pixelShader)
            checkGlError("glAttachShader: pixel")
            return linkProgram()
        }
        return true
    }

    private fun linkProgram(): Boolean {
        if (program == UNKNOWN_PROGRAM) {
            return false
        }
        GLES30.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES30.GL_TRUE) {
            Log.e(TAG, "Could not link program: ")
            Log.e(TAG, GLES30.glGetProgramInfoLog(program))
            GLES30.glDeleteProgram(program)
            program = UNKNOWN_PROGRAM
            return false
        }
        return true
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES30.glCreateShader(shaderType)
        if (shader != UNKNOWN_PROGRAM) {
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == UNKNOWN_PROGRAM) {
                Log.e(TAG, "Could not compile shader $shaderType:")
                Log.e(TAG, GLES30.glGetShaderInfoLog(shader))
                GLES30.glDeleteShader(shader)
                shader = UNKNOWN_PROGRAM
            }
        }
        return shader
    }

    private fun checkGlError(op: String) {
        var error: Int
        while (GLES30.glGetError().also { error = it } != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError $error")
            throw RuntimeException("$op: glError $error")
        }
    }

    /**
     * set values for attributes of input vertices
     */
    private fun setAttribute(attrLocation: Int, attrName: String, size: Int, offset: Int) {
        if (attrLocation == UNKNOWN_ATTRIBUTE) {
            // skip it if undefined
            return
        }
        quadVertices.position(offset)
        GLES30.glVertexAttribPointer(
            attrLocation,
            size,
            GLES30.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            quadVertices
        )
        checkGlError("glVertexAttribPointer $attrName")
        GLES30.glEnableVertexAttribArray(attrLocation)
        checkGlError("glEnableVertexAttribArray $attrName")
    }

    private fun Int.convertTextureSlotToIndex(): Int =
        when (this) {
            GLES30.GL_TEXTURE0 -> 0
            GLES30.GL_TEXTURE1 -> 1
            GLES30.GL_TEXTURE2 -> 2
            GLES30.GL_TEXTURE3 -> 3
            GLES30.GL_TEXTURE4 -> 4
            GLES30.GL_TEXTURE5 -> 5
            GLES30.GL_TEXTURE6 -> 6
            GLES30.GL_TEXTURE7 -> 7
            GLES30.GL_TEXTURE8 -> 8
            GLES30.GL_TEXTURE9 -> 9
            GLES30.GL_TEXTURE10 -> 10
            GLES30.GL_TEXTURE11 -> 11
            GLES30.GL_TEXTURE12 -> 12
            GLES30.GL_TEXTURE13 -> 13
            GLES30.GL_TEXTURE14 -> 14
            GLES30.GL_TEXTURE15 -> 15
            GLES30.GL_TEXTURE16 -> 16
            GLES30.GL_TEXTURE17 -> 17
            GLES30.GL_TEXTURE18 -> 18
            GLES30.GL_TEXTURE19 -> 19
            GLES30.GL_TEXTURE20 -> 20
            GLES30.GL_TEXTURE21 -> 21
            GLES30.GL_TEXTURE22 -> 22
            GLES30.GL_TEXTURE23 -> 23
            GLES30.GL_TEXTURE24 -> 24
            GLES30.GL_TEXTURE25 -> 25
            GLES30.GL_TEXTURE26 -> 26
            GLES30.GL_TEXTURE27 -> 27
            GLES30.GL_TEXTURE28 -> 28
            GLES30.GL_TEXTURE29 -> 29
            GLES30.GL_TEXTURE30 -> 30
            GLES30.GL_TEXTURE31 -> 31
            else -> 0
        }
}