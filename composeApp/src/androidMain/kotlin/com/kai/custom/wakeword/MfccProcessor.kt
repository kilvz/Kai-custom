package com.kai.custom.wakeword

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.log10
import kotlin.math.PI
import kotlin.math.sqrt

class MfccProcessor(
    private val sampleRate: Int = 16000,
    private val numMfcc: Int = 13,
    private val numFrames: Int = 49,
    private val nfft: Int = 512,
    private val nfilt: Int = 40,
    private val lowFreq: Double = 0.0,
    private val highFreq: Double = 8000.0,
    private val windowSizeMs: Double = 30.0,
    private val windowStepMs: Double = 20.0,
) {
    private val preemphCoeff = 0.97f
    private val windowSize: Int = (sampleRate * windowSizeMs / 1000.0).toInt()
    private val windowStep: Int = (sampleRate * windowStepMs / 1000.0).toInt()
    private val hammingWindow: FloatArray
    private val melFilterbank: Array<FloatArray>
    private val dctMatrix: Array<FloatArray>

    init {
        hammingWindow = computeHamming(windowSize)
        melFilterbank = computeMelFilterbank()
        dctMatrix = computeDctMatrix()
    }

    fun compute(audio: FloatArray, length: Int): Array<FloatArray> {
        val emphasized = FloatArray(length)
        emphasized[0] = audio[0]
        for (i in 1 until length) {
            emphasized[i] = audio[i] - preemphCoeff * audio[i - 1]
        }

        val numFramesActual = maxOf(1, 1 + (length - windowSize) / windowStep)
        val frames = Array(numFramesActual) { f ->
            val start = f * windowStep
            FloatArray(windowSize) { i ->
                if (start + i < length) emphasized[start + i] * hammingWindow[i] else 0f
            }
        }

        val pspec = frames.map { frame ->
            val spectrum = fft(frame)
            FloatArray(nfft / 2 + 1) { i ->
                val re = spectrum[i * 2]
                val im = spectrum[i * 2 + 1]
                re * re + im * im
            }
        }

        val feat = pspec.map { spec ->
            FloatArray(nfilt) { j ->
                var sum = 0f
                for (k in spec.indices) {
                    sum += spec[k] * melFilterbank[j][k]
                }
                if (sum < 1e-10f) sum = 1e-10f
                log10(sum.toDouble()).toFloat()
            }
        }

        val result = feat.map { frame ->
            FloatArray(numMfcc) { i ->
                var sum = 0f
                for (j in frame.indices) {
                    sum += frame[j] * dctMatrix[i][j]
                }
                sum
            }
        }

        val out = Array(numFrames) { f ->
            if (f < result.size) result[f] else FloatArray(numMfcc)
        }
        return out
    }

    private fun fft(frame: FloatArray): FloatArray {
        val n = frame.size
        var actualSize = 1
        while (actualSize < n) actualSize *= 2
        val size = minOf(actualSize, nfft)

        val re = FloatArray(size)
        val im = FloatArray(size)
        for (i in 0 until minOf(n, size)) {
            re[i] = frame[i]
        }

        fftRadix2(re, im, false)
        return FloatArray(size * 2) { i ->
            if (i % 2 == 0) re[i / 2] else im[i / 2]
        }
    }

    private fun fftRadix2(re: FloatArray, im: FloatArray, invert: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[j]; val ti = im[j]
                re[j] = re[i]; im[j] = im[i]
                re[i] = tr; im[i] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = if (invert) 2.0 * PI / len else -2.0 * PI / len
            val wlenRe = cos(angle).toFloat()
            val wlenIm = sin(angle).toFloat()
            for (i in 0 until n step len) {
                var wRe = 1f
                var wIm = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val uRe = re[i + k]; val uIm = im[i + k]
                    val vRe = re[i + k + half] * wRe - im[i + k + half] * wIm
                    val vIm = re[i + k + half] * wIm + im[i + k + half] * wRe
                    re[i + k] = uRe + vRe; im[i + k] = uIm + vIm
                    re[i + k + half] = uRe - vRe; im[i + k + half] = uIm - vIm
                    val nwRe = wRe * wlenRe - wIm * wlenIm
                    val nwIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nwRe; wIm = nwIm
                }
            }
            len *= 2
        }

        if (invert) {
            for (i in 0 until n) {
                re[i] /= n; im[i] /= n
            }
        }
    }

    private fun computeHamming(size: Int): FloatArray {
        return FloatArray(size) { i ->
            (0.54 - 0.46 * cos(2.0 * PI * i / (size - 1))).toFloat()
        }
    }

    private fun melToHz(mel: Double): Double = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)

    private fun computeMelFilterbank(): Array<FloatArray> {
        val lowMel = hzToMel(lowFreq)
        val highMel = hzToMel(highFreq)
        val melPoints = DoubleArray(nfilt + 2) { i ->
            lowMel + (highMel - lowMel) * i / (nfilt + 1)
        }
        val hzPoints = melPoints.map { melToHz(it) }.toDoubleArray()
        val bin = hzPoints.map { ((nfft + 1) * it / sampleRate).toInt() }.toIntArray()

        val fbank = Array(nfilt) { FloatArray(nfft / 2 + 1) }
        for (j in 0 until nfilt) {
            for (i in bin[j]..bin[j + 1]) {
                if (i < fbank[j].size) {
                    fbank[j][i] = (i - bin[j]).toFloat() / (bin[j + 1] - bin[j]).toFloat()
                }
            }
            for (i in bin[j + 1]..bin[j + 2]) {
                if (i < fbank[j].size) {
                    fbank[j][i] = (bin[j + 2] - i).toFloat() / (bin[j + 2] - bin[j + 1]).toFloat()
                }
            }
        }
        return fbank
    }

    private fun computeDctMatrix(): Array<FloatArray> {
        val n = nfilt
        val m = numMfcc
        val matrix = Array(m) { k ->
            FloatArray(n) { i ->
                val v = (cos(PI * k * (i + 0.5) / n) * sqrt(2.0 / n)).toFloat()
                if (k == 0) v / sqrt(2.0).toFloat() else v
            }
        }
        return matrix
    }
}
