package com.openpad.core.frequency

/**
 * Result from screen display detection analysis.
 *
 * @param screenScore Combined score indicating screen likelihood [0..1]. Higher = more likely screen.
 * @param uniformity Focus uniformity score [0..1]. High = uniform focus = screen.
 * @param entropy Color banding score [0..1]. High = more banding = screen.
 * @param channelCorrelation Color distribution score [0..1]. High = compressed chrominance = screen.
 */
data class LbpResult(
    val screenScore: Float,
    val uniformity: Float,
    val entropy: Float,
    val channelCorrelation: Float
)
