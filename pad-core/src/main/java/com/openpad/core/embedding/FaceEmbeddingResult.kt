package com.openpad.core.embedding

/**
 * Result from the face embedding model.
 *
 * @param embedding 128-dimensional L2-normalized face embedding vector.
 *                  Null when model is in placeholder mode.
 */
data class FaceEmbeddingResult(
    val embedding: FloatArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceEmbeddingResult) return false
        return embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int = embedding?.contentHashCode() ?: 0
}
