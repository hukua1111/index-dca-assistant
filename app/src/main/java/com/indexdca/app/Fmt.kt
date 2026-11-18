package com.indexdca.app

import java.util.Locale

object Fmt {
    fun p1(v: Double?): String = v?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
    fun p2(v: Double?): String = v?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
    fun p4(v: Double?): String = v?.let { String.format(Locale.US, "%.4f", it) } ?: "—"
    fun signed1(v: Double?): String = v?.let { String.format(Locale.US, "%+.1f", it) } ?: "—"
}
