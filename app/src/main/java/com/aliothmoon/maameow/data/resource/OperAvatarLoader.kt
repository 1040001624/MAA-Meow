package com.aliothmoon.maameow.data.resource

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aliothmoon.maameow.data.config.MaaPathConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 干员头像加载器，对应 WPF OperAvatarHelper.GetOperAvatar
 *
 * template/avatar/{operId}.png，热更目录优先：新干员的头像先随热更到
 */
class OperAvatarLoader(
    private val pathConfig: MaaPathConfig
) {
    // 120×120 ARGB 约 58KB 一张
    private val cache = LruCache<String, ImageBitmap>(256)

    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun peek(operId: String): ImageBitmap? = cache.get(operId)

    suspend fun load(operId: String): ImageBitmap? {
        if (operId.isEmpty()) return null
        cache.get(operId)?.let { return it }
        if (operId in missing) return null

        return withContext(Dispatchers.IO) {
            // 双重检查：可能在切线程期间已被其他协程填充
            cache.get(operId)?.let { return@withContext it }
            if (operId in missing) return@withContext null

            val file = sequenceOf(pathConfig.cacheResourceDir, pathConfig.resourceDir)
                .map { File(it, "$AVATAR_DIR/$operId.png") }
                .firstOrNull { it.isFile }
            val bitmap = try {
                file?.let { BitmapFactory.decodeFile(it.absolutePath) }
            } catch (e: Exception) {
                Timber.w(e, "加载干员头像失败: $operId")
                null
            }
            if (bitmap == null) {
                missing.add(operId)
                return@withContext null
            }
            // 提前上传纹理
            bitmap.prepareToDraw()
            bitmap.asImageBitmap().also { cache.put(operId, it) }
        }
    }

    private companion object {
        const val AVATAR_DIR = "template/avatar"
    }
}
