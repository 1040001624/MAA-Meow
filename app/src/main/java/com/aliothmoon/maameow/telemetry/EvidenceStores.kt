package com.aliothmoon.maameow.telemetry

import android.os.ParcelFileDescriptor
import com.aliothmoon.maameow.RemoteService
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** 数据目录没分开时 coreDir 即 appDir */
internal class LocalEvidenceStore(
    private val appDir: File,
    private val coreDir: File = appDir,
) : EvidenceStore {

    private fun resolve(path: String, core: Boolean) = File(if (core) coreDir else appDir, path)

    override fun length(file: EvidenceFile): Long? =
        resolve(file.path, file.core).takeIf { it.isFile && it.canRead() }?.length()

    override fun read(file: EvidenceFile, from: Long, length: Int): ByteArray =
        RandomAccessFile(resolve(file.path, file.core), "r").use { raf ->
            raf.seek(from)
            readUpTo(length, raf::read)
        }

    override fun list(dir: String, core: Boolean): List<String>? {
        val directory = resolve(dir, core)
        if (!directory.exists()) return emptyList()
        return directory.listFiles()?.filter { it.isFile }?.map { it.name }
    }
}

/**
 * 经提权进程取独立数据目录（`/data/local/tmp`）下的 debug 文件
 *
 * 这条路只认那一个目录，数据目录没独立时不能拿它当回退
 */
internal class RemoteEvidenceStore(private val service: () -> RemoteService?) : EvidenceStore {

    private fun <T> open(path: String, block: (ParcelFileDescriptor) -> T): T? {
        val pfd = runCatching { service()?.openCoreDebugFile(path) }.getOrNull() ?: return null
        return pfd.use(block)
    }

    override fun length(file: EvidenceFile): Long? = open(file.path) { it.statSize }?.takeIf { it >= 0 }

    override fun read(file: EvidenceFile, from: Long, length: Int): ByteArray =
        open(file.path) { pfd ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd.dup()).use { input ->
                input.channel.position(from)
                readUpTo(length, input::read)
            }
        } ?: throw IOException("remote file unavailable")

    override fun list(dir: String, core: Boolean): List<String>? {
        val prefix = "$dir/"
        return runCatching { service()?.listCoreDebugFiles() }.getOrNull()
            ?.filter { it.startsWith(prefix) && '/' !in it.substring(prefix.length) }
            ?.map { it.substring(prefix.length) }
    }
}

/** Core 侧的文件在数据目录独立时走提权进程，其余直接读 */
internal class RoutingEvidenceStore(
    private val local: EvidenceStore,
    private val remote: EvidenceStore,
    private val coreSeparated: Boolean,
) : EvidenceStore {

    private fun storeFor(core: Boolean) = if (core && coreSeparated) remote else local

    override fun length(file: EvidenceFile): Long? = storeFor(file.core).length(file)

    override fun read(file: EvidenceFile, from: Long, length: Int): ByteArray =
        storeFor(file.core).read(file, from, length)

    override fun list(dir: String, core: Boolean): List<String>? = storeFor(core).list(dir, core)
}

/** 文件可能在量完之后被截短，读到多少算多少 */
private inline fun readUpTo(length: Int, read: (ByteArray, Int, Int) -> Int): ByteArray {
    val buffer = ByteArray(length)
    var filled = 0
    while (filled < length) {
        val count = read(buffer, filled, length - filled)
        if (count < 0) break
        filled += count
    }
    return if (filled == length) buffer else buffer.copyOf(filled)
}
