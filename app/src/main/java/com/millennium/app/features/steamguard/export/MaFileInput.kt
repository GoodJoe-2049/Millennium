package com.millennium.app.features.steamguard.export

import java.io.InputStream

<<<<<<< HEAD
/** Bounded reader for maFile imports and the encrypted account store. */
=======
/** Bounded plaintext reader shared by document and consented app-to-app imports. */
>>>>>>> Good-Joe2049/main
internal object MaFileInput {
    const val MAX_BYTES = 1024 * 1024

    fun readBytes(stream: InputStream, limit: Int = MAX_BYTES): ByteArray {
<<<<<<< HEAD
        // Imports default to 1 MiB; the account store supplies its own larger limit.
        // Leave room for the extra byte used to detect oversized input.
        require(limit in 1 until Int.MAX_VALUE)
=======
        require(limit in 1..MAX_BYTES)
>>>>>>> Good-Joe2049/main
        val buffer = ByteArray(limit + 1)
        try {
            var size = 0
            while (true) {
                val count = stream.read(buffer, size, buffer.size - size)
                if (count == -1) break
                if (count == 0) {
                    val byte = stream.read()
                    if (byte == -1) break
                    buffer[size++] = byte.toByte()
                } else size += count
                require(size <= limit)
            }
            return buffer.copyOf(size)
        } finally { buffer.fill(0) }
    }
}
