package com.securebroadcast.compressor

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.util.Log

import com.securebroadcast.compressor.videocompression.MediaController

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.URISyntaxException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * @author Toure, Akah L
 * @version 1.1.1
 * Created by Toure on 28/03/2016.
 */
class SiliCompressor(context: Context) {

    init {
        mContext = context
    }

    @Throws(URISyntaxException::class)
    @JvmOverloads
    fun compressVideo(videoFilePath: String, destinationDir: String, outWidth: Int = 0, outHeight: Int = 0, bitrate: Int = 0): String {
        val isconverted = MediaController.instance.convertVideo(videoFilePath, File(destinationDir), outWidth, outHeight, bitrate)
        if (isconverted) {
            Log.v(LOG_TAG, "Video Conversion Complete")
        } else {
            Log.v(LOG_TAG, "Video conversion in progress")
        }

        return MediaController.cachedFile.path

    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }
        val totalPixels = (width * height).toFloat()
        val totalReqPixelsCap = (reqWidth * reqHeight * 2).toFloat()
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++
        }

        return inSampleSize
    }

    class Builder(context: Context?) {
        private val context: Context

        init {
            if (context == null) {
                throw IllegalArgumentException("Context must not be null.")
            }
            this.context = context.applicationContext
        }

        fun build(): SiliCompressor {
            val context = this.context
            return SiliCompressor(context)
        }
    }

    companion object {

        private val LOG_TAG = SiliCompressor::class.java.simpleName
        var videoCompressionPath: String? = null

        @Volatile
        internal var singleton: SiliCompressor? = null
        private lateinit var mContext: Context
        val FILE_PROVIDER_AUTHORITY = "com.iceteck.silicompressor.provider"

        // initialise the class and set the context
        fun with(context: Context): SiliCompressor? {
            if (singleton == null) {
                synchronized(SiliCompressor::class.java) {
                    if (singleton == null) {
                        singleton = Builder(context).build()
                    }
                }
            }
            return singleton
        }
    }
}
