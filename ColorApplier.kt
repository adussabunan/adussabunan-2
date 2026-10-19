package com.chiara.beep

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView

object ColorApplier {

    /**
     * Terapkan warna ke seluruh ViewGroup secara rekursif.
     * Semua TextView, Button, RadioButton, Switch mengikuti warna yang dipilih.
     * Tombol STOP dan elemen khusus dikecualikan via tag.
     */
    fun apply(root: ViewGroup, prefs: PrefsHelper) {
        val bright = Color.parseColor(prefs.getColorHex())
        val dim    = Color.parseColor(prefs.getColorDimHex())
        val faint  = Color.parseColor(prefs.getColorFaintHex())
        val bg     = Color.parseColor(prefs.getColorBgHex())
        val csl    = ColorStateList.valueOf(bright)
        val cslDim = ColorStateList.valueOf(dim)

        applyToGroup(root, bright, dim, faint, bg, csl, cslDim)
    }

    private fun applyToGroup(
        group: ViewGroup,
        bright: Int, dim: Int, faint: Int, bg: Int,
        csl: ColorStateList, cslDim: ColorStateList
    ) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)

            // Skip elemen yang diberi tag "no_color"
            if (child.tag == "no_color") continue

            when (child) {
                is ViewGroup -> applyToGroup(child, bright, dim, faint, bg, csl, cslDim)

                is RadioButton -> {
                    child.setTextColor(bright)
                    child.buttonTintList = csl
                }

                is Switch -> {
                    child.thumbTintList = csl
                    child.trackTintList = ColorStateList.valueOf(bg)
                }

                is Button -> {
                    val tag = child.tag?.toString() ?: ""
                    when {
                        tag == "btn_start" -> {
                            // START: teks hitam, background bright
                            child.setTextColor(Color.BLACK)
                            child.backgroundTintList = csl
                        }
                        tag == "btn_stop" -> {
                            // STOP: selalu merah, tidak ikut warna tema
                        }
                        tag == "btn_test" -> {
                            child.setTextColor(bright)
                            child.backgroundTintList = ColorStateList.valueOf(bg)
                        }
                        else -> {
                            child.setTextColor(bright)
                            child.backgroundTintList = ColorStateList.valueOf(bg)
                        }
                    }
                }

                is TextView -> {
                    val tag = child.tag?.toString() ?: ""
                    when (tag) {
                        "label_faint"  -> child.setTextColor(faint)
                        "label_dim"    -> child.setTextColor(dim)
                        "time_second"  -> child.setTextColor(dim)
                        "silent_time"  -> {
                            child.setTextColor(bright)
                            child.backgroundTintList = ColorStateList.valueOf(bg)
                        }
                        else -> child.setTextColor(bright)
                    }
                }

                is ProgressBar -> {
                    child.progressTintList = csl
                }
            }
        }
    }
}
