package com.example.galaxylut

import android.graphics.Bitmap
import kotlin.math.floor

/** Minimal .cube 3D LUT parser and trilinear RGB transform. */
class CubeLut private constructor(private val size: Int, private val table: FloatArray,
                                  private val domainMin: FloatArray, private val domainMax: FloatArray) {
    fun apply(source: Bitmap): Bitmap {
        val w = source.width; val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = ((c shr 16) and 255) / 255f
            val g = ((c shr 8) and 255) / 255f
            val b = (c and 255) / 255f
            val out = sample(r, g, b)
            pixels[i] = (c and -0x1000000) or
                ((out[0] * 255f).toInt().coerceIn(0,255) shl 16) or
                ((out[1] * 255f).toInt().coerceIn(0,255) shl 8) or
                (out[2] * 255f).toInt().coerceIn(0,255)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun sample(rr: Float, gg: Float, bb: Float): FloatArray {
        fun norm(v: Float, ch: Int): Float = ((v - domainMin[ch]) / (domainMax[ch] - domainMin[ch]).coerceAtLeast(1e-6f)).coerceIn(0f,1f) * (size - 1)
        val x=norm(rr,0); val y=norm(gg,1); val z=norm(bb,2)
        val x0=floor(x).toInt(); val y0=floor(y).toInt(); val z0=floor(z).toInt()
        val x1=(x0+1).coerceAtMost(size-1); val y1=(y0+1).coerceAtMost(size-1); val z1=(z0+1).coerceAtMost(size-1)
        val fx=x-x0; val fy=y-y0; val fz=z-z0
        fun at(ix:Int, iy:Int, iz:Int, ch:Int)=table[((iz*size+iy)*size+ix)*3+ch]
        val out=FloatArray(3)
        for(ch in 0..2) {
            val c00=at(x0,y0,z0,ch)*(1-fx)+at(x1,y0,z0,ch)*fx
            val c10=at(x0,y1,z0,ch)*(1-fx)+at(x1,y1,z0,ch)*fx
            val c01=at(x0,y0,z1,ch)*(1-fx)+at(x1,y0,z1,ch)*fx
            val c11=at(x0,y1,z1,ch)*(1-fx)+at(x1,y1,z1,ch)*fx
            val c0=c00*(1-fy)+c10*fy; val c1=c01*(1-fy)+c11*fy
            out[ch]=(c0*(1-fz)+c1*fz).coerceIn(0f,1f)
        }
        return out
    }

    companion object {
        fun parse(text: String): CubeLut {
            var size=0; val values=ArrayList<FloatArray>(); val min=floatArrayOf(0f,0f,0f); val max=floatArrayOf(1f,1f,1f)
            text.lineSequence().forEach { raw ->
                val line=raw.substringBefore('#').trim(); if(line.isEmpty()) return@forEach
                val p=line.split(Regex("\\s+")); when(p[0].uppercase()) {
                    "TITLE" -> Unit
                    "LUT_3D_SIZE" -> size=p.getOrNull(1)?.toIntOrNull() ?: 0
                    "DOMAIN_MIN" -> for(i in 0..2) min[i]=p.getOrNull(i+1)?.toFloatOrNull() ?: min[i]
                    "DOMAIN_MAX" -> for(i in 0..2) max[i]=p.getOrNull(i+1)?.toFloatOrNull() ?: max[i]
                    "LUT_1D_SIZE" -> throw IllegalArgumentException("Chỉ hỗ trợ LUT 3D .cube")
                    else -> if(p.size>=3) {
                        val rgb=FloatArray(3) { p[it].toFloatOrNull() ?: Float.NaN }
                        if(rgb.all { it.isFinite() }) values.add(rgb)
                    }
                }
            }
            require(size in 2..65) { "LUT_3D_SIZE không hợp lệ" }
            require(values.size==size*size*size) { "Số điểm LUT không khớp kích thước $size³" }
            val table=FloatArray(values.size*3); values.forEachIndexed { i,v -> for(c in 0..2) table[i*3+c]=v[c].coerceIn(0f,1f) }
            return CubeLut(size,table,min,max)
        }
    }
}
