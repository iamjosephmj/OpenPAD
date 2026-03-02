/**
 * @file config.h
 * @brief Configuration parsing and defaults for the OpenPAD pipeline.
 *
 * The Kotlin layer serializes PadConfig into a 172-byte little-endian buffer:
 *   bytes   0-83  : 21 float32 values (thresholds, weights)
 *   bytes  84-127 : padding (zeros)
 *   bytes 128-171 : 11 int32 values (frame counts, window sizes)
 *
 * @see OpadConfig in types.h
 */

#ifndef OPENPAD_CONFIG_H
#define OPENPAD_CONFIG_H

#include "types.h"

/**
 * Fill @p out with production-tuned defaults.
 * Used as fallback when the config buffer is too short.
 */
void opad_config_default(OpadConfig* out);

/**
 * Parse a 172-byte little-endian config buffer into @p out.
 *
 * @param bytes  Raw buffer from Kotlin (little-endian).
 * @param len    Length of the buffer; must be >= 172.
 * @param out    Destination config struct.
 * @return true if parsing succeeded, false if @p len < 172 (defaults applied).
 */
bool opad_config_parse(const uint8_t* bytes, size_t len, OpadConfig* out);

#endif /* OPENPAD_CONFIG_H */
