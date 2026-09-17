package com.flickbeam.tv.network

import java.io.InputStream

/** Writes an incoming file to storage. Returns the final saved file name. */
interface IncomingFiles {
    /** [onProgress] is called with 0..100 as bytes arrive (only when the size is known). */
    fun save(name: String, input: InputStream, length: Long, onProgress: (Int) -> Unit): String
}
