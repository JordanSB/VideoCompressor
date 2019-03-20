package com.securebroadcast.compressor.videocompression

import android.annotation.TargetApi
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Build
import android.view.Surface

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class InputSurface(surface: Surface?) {
    private var mEGLDisplay: EGLDisplay? = null
    private var mEGLContext: EGLContext? = null
    private var mEGLSurface: EGLSurface? = null
    var surface: Surface? = null
        private set

    init {
        if (surface == null) {
            throw NullPointerException()
        }
        this.surface = surface
        eglSetup()
    }

    private fun eglSetup() {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEGLDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null
            throw RuntimeException("unable to initialize EGL14")
        }

        val attribList = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.size,
                        numConfigs, 0)) {
            throw RuntimeException("unable to find RGB888+recordable ES2 EGL config")
        }

        val attrib_list = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)

        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT, attrib_list, 0)
        checkEglError("eglCreateContext")
        if (mEGLContext == null) {
            throw RuntimeException("null context")
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], surface,
                surfaceAttribs, 0)
        checkEglError("eglCreateWindowSurface")
        if (mEGLSurface == null) {
            throw RuntimeException("surface was null")
        }
    }

    fun release() {
        if (EGL14.eglGetCurrentContext() == mEGLContext) {
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        }
        EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface)
        EGL14.eglDestroyContext(mEGLDisplay, mEGLContext)
        surface!!.release()
        mEGLDisplay = null
        mEGLContext = null
        mEGLSurface = null
        surface = null
    }

    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun swapBuffers(): Boolean {
        return EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
    }

    fun setPresentationTime(nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs)
    }

    private fun checkEglError(msg: String) {
        var failed = false
        var error: Int
        while (EGL14.eglGetError() != EGL14.EGL_SUCCESS) {
            failed = true
        }
        if (failed) {
            throw RuntimeException("EGL error encountered (see log)")
        }
    }

    companion object {
        private val VERBOSE = false
        private val EGL_RECORDABLE_ANDROID = 0x3142
        private val EGL_OPENGL_ES2_BIT = 4
    }
}
