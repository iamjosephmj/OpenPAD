package com.openpad.core.photometric

/**
 * Result from photometric analysis of the face region.
 *
 * Four independent signals that detect spoofs via light/surface properties:
 *
 * @param specularScore Specular highlight naturalness [0..1].
 *   Real faces: soft, localized highlights on nose/forehead/cheeks.
 *   Screens: uniform backlight glow, no localized specular.
 *   Prints: no specular highlights at all (matte surface).
 *   High = more natural specular = more likely real.
 *
 * @param chrominanceScore Skin chrominance spread [0..1].
 *   Real skin: broad YCbCr distribution from subsurface scattering + ambient light.
 *   Screens: compressed gamut, tighter clustering.
 *   High = more natural spread = more likely real.
 *
 * @param edgeDofScore Edge depth-of-field gradient [0..1].
 *   Real faces: face boundary is softer than face center (natural DOF).
 *   Screens: uniform sharpness everywhere (flat focal plane).
 *   High = more DOF gradient = more likely real.
 *
 * @param lightingScore Lighting direction consistency [0..1].
 *   Real faces: consistent light direction (brighter on one side, shadow on other).
 *   Screens: light comes from the screen itself, creating unnatural symmetry
 *   or mixed lighting from screen + ambient.
 *   High = more consistent natural lighting = more likely real.
 *
 * @param combinedScore Fused photometric score [0..1]. High = more likely real.
 */
data class PhotometricResult(
    val specularScore: Float,
    val chrominanceScore: Float,
    val edgeDofScore: Float,
    val lightingScore: Float,
    val combinedScore: Float
)
