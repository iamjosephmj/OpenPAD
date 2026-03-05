package com.openpad.core.di

import com.openpad.core.OpenPadThemeConfig
import com.openpad.core.ui.viewmodel.PadSessionCallback

/**
 * Temporary holder for per-session parameters that cannot be passed via Intent extras.
 *
 * Set by [OpenPad.analyze] just before starting [PadActivity], then consumed
 * and cleared immediately in [PadActivity.onCreate].
 */
internal object PadSessionHolder {

    @Volatile
    var pending: Params? = null

    internal data class Params(
        val sessionStartMs: Long,
        val callback: PadSessionCallback,
        val themeConfig: OpenPadThemeConfig
    )
}
