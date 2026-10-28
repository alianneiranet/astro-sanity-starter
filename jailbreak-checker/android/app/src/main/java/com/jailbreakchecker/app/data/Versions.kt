package com.jailbreakchecker.app.data

import java.math.BigDecimal

/**
 * Firmware version comparison that understands the formats consoles actually use:
 *  - PlayStation style "X.YY" (9.03 < 9.5 == 9.50 < 9.60): two parts, minor compared as a decimal fraction.
 *  - Dotted versions "23.0.1", "11.17.0-50", "10.0.25398.4909": compared number by number.
 */
object Versions {
    private val number = Regex("\\d+")

    fun parts(version: String): List<String> = number.findAll(version).map { it.value }.toList()

    fun isValid(version: String): Boolean = parts(version).isNotEmpty()

    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        if (pa.size == 2 && pb.size == 2) {
            val major = pa[0].toBigInteger().compareTo(pb[0].toBigInteger())
            if (major != 0) return major
            return BigDecimal("0." + pa[1]).compareTo(BigDecimal("0." + pb[1]))
        }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i)?.toBigInteger() ?: java.math.BigInteger.ZERO
            val y = pb.getOrNull(i)?.toBigInteger() ?: java.math.BigInteger.ZERO
            val c = x.compareTo(y)
            if (c != 0) return c
        }
        return 0
    }

    fun inRange(version: String, range: FirmwareRange): Boolean {
        if (range.isCatchAll) return true
        val aboveMin = range.min.isBlank() || compare(version, range.min) >= 0
        val belowMax = range.max.isBlank() || compare(version, range.max) <= 0
        return aboveMin && belowMax
    }

    /**
     * Finds the range covering [version]. Ranges with explicit bounds win over
     * "any other version" catch-alls. Returns null if nothing matches.
     */
    fun match(version: String, ranges: List<FirmwareRange>): FirmwareRange? {
        if (!isValid(version)) return null
        return ranges.firstOrNull { !it.isCatchAll && inRange(version, it) }
            ?: ranges.firstOrNull { it.isCatchAll }
    }
}
