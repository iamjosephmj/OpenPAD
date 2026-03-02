/**
 * @file stabilizer.c
 * @brief Hysteresis state stabilizer for PAD status.
 *
 * Prevents status flickering by requiring N consecutive identical
 * per-frame classifications before committing a transition:
 *
 *   - Transition TO LIVE requires `exit_consecutive` frames.
 *   - Transition TO any other status requires `enter_consecutive` frames.
 *   - ANALYZING is ignored (not counted, preserves current state).
 *   - NO_FACE commits immediately (hard override).
 *
 * The consecutive counter resets whenever the candidate changes.
 */

#include <openpad/aggregation.h>
#include <stdlib.h>

struct OpadStateStabilizer {
    OpadPadStatus current;
    OpadPadStatus candidate;
    int32_t       consecutive;
};

OpadStateStabilizer* opad_stabilizer_create(OpadPadStatus initial) {
    OpadStateStabilizer* s = (OpadStateStabilizer*)calloc(1, sizeof(*s));
    if (!s) return NULL;
    s->current   = initial;
    s->candidate = initial;
    s->consecutive = 0;
    return s;
}

void opad_stabilizer_destroy(OpadStateStabilizer* s) { free(s); }

static void commit(OpadStateStabilizer* s, OpadPadStatus st) {
    s->current     = st;
    s->candidate   = st;
    s->consecutive = 0;
}

OpadPadStatus opad_stabilizer_update(OpadStateStabilizer* s,
                                      OpadPadStatus candidate,
                                      int32_t enter_consecutive,
                                      int32_t exit_consecutive) {
    if (candidate == OPAD_STATUS_ANALYZING) return s->current;
    if (candidate == OPAD_STATUS_NO_FACE) {
        commit(s, OPAD_STATUS_NO_FACE);
        return s->current;
    }
    if (candidate == s->current) {
        int32_t cap = enter_consecutive > exit_consecutive
                      ? enter_consecutive : exit_consecutive;
        s->consecutive++;
        if (s->consecutive > cap) s->consecutive = cap;
        return s->current;
    }

    int32_t needed = (candidate == OPAD_STATUS_LIVE)
                     ? exit_consecutive : enter_consecutive;

    if (candidate != s->candidate) {
        s->candidate   = candidate;
        s->consecutive = 1;
    } else {
        s->consecutive++;
    }
    if (s->consecutive >= needed) commit(s, candidate);
    return s->current;
}

void opad_stabilizer_reset(OpadStateStabilizer* s) {
    s->current     = OPAD_STATUS_ANALYZING;
    s->candidate   = OPAD_STATUS_ANALYZING;
    s->consecutive = 0;
}
