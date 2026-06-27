package com.netspeed.indicator.core

/**
 * Pure geometry for docking the floating speed bubble into the status bar — the
 * "clever spot" the indicator ships in by default: tucked just RIGHT of a centred
 * display cutout (the Galaxy punch-hole) and LEFT of the right-side system-icon
 * cluster (Wi-Fi / signal / battery). On phones with no central cutout it parks
 * just left of that same system-icon cluster.
 *
 * All values are window pixels; the returned x is the chip's LEFT edge (the
 * overlay uses gravity TOP|START). No Android types so it unit-tests on the JVM.
 */
object BubbleDock {

    /** Share of the width reserved on the right for the system icons — the chip
     *  is always kept left of this band. */
    private const val SYSTEM_ICONS_FRACTION = 22   // percent

    /**
     * @param screenWidthPx display width
     * @param statusBarPx   status-bar height (chip is vertically centred in it)
     * @param chipWidthPx   measured/estimated bubble width
     * @param chipHeightPx  measured/estimated bubble height
     * @param cutoutLeftPx  left edge of a top display cutout, or null if none
     * @param cutoutRightPx right edge of a top display cutout, or null if none
     * @param gapPx         breathing room from the cutout / icons / screen edge
     */
    fun besideNotch(
        screenWidthPx: Int,
        statusBarPx: Int,
        chipWidthPx: Int,
        chipHeightPx: Int,
        cutoutLeftPx: Int?,
        cutoutRightPx: Int?,
        gapPx: Int,
    ): Pair<Int, Int> {
        val y = ((statusBarPx - chipHeightPx) / 2).coerceAtLeast(0)
        // Only a roughly-central cutout (a punch-hole) is worth tucking beside;
        // a corner notch is treated as "no central cutout".
        val central = cutoutLeftPx != null && cutoutRightPx != null &&
            cutoutLeftPx > screenWidthPx / 4 && cutoutRightPx < screenWidthPx * 3 / 4
        // Never let the chip slide under the system-icon band on the right.
        val systemIconsLeft = screenWidthPx - (screenWidthPx * SYSTEM_ICONS_FRACTION / 100)
        val maxX = (systemIconsLeft - chipWidthPx - gapPx).coerceAtLeast(gapPx)
        val rawX = if (central) cutoutRightPx!! + gapPx else maxX
        return rawX.coerceIn(gapPx, maxX) to y
    }
}
