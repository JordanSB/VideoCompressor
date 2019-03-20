/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package com.securebroadcast.compressor.videocompression

import android.annotation.TargetApi
import android.media.MediaCodec
import android.media.MediaFormat

import com.googlecode.mp4parser.util.Matrix

import java.io.File
import java.util.ArrayList

@TargetApi(16)
class Mp4Movie {
    var matrix = Matrix.ROTATE_0
        private set
    val tracks = ArrayList<Track>()
    var cacheFile: File? = null
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    fun setRotation(angle: Int) {
        if (angle == 0) {
            matrix = Matrix.ROTATE_0
        } else if (angle == 90) {
            matrix = Matrix.ROTATE_90
        } else if (angle == 180) {
            matrix = Matrix.ROTATE_180
        } else if (angle == 270) {
            matrix = Matrix.ROTATE_270
        }
    }

    fun setSize(w: Int, h: Int) {
        width = w
        height = h
    }

    @Throws(Exception::class)
    fun addSample(trackIndex: Int, offset: Long, bufferInfo: MediaCodec.BufferInfo) {
        if (trackIndex < 0 || trackIndex >= tracks.size) {
            return
        }
        val track = tracks[trackIndex]
        track.addSample(offset, bufferInfo)
    }

    @Throws(Exception::class)
    fun addTrack(mediaFormat: MediaFormat, isAudio: Boolean): Int {
        tracks.add(Track(tracks.size, mediaFormat, isAudio))
        return tracks.size - 1
    }
}
