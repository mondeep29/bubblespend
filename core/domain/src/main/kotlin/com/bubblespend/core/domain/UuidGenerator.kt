package com.bubblespend.core.domain

import java.util.UUID

/** Caller-supplied source of IDs, allowing deterministic domain tests. */
fun interface UuidGenerator {
    fun next(): UUID
}
