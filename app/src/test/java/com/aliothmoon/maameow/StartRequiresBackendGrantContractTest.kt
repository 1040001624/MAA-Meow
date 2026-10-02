package com.aliothmoon.maameow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 后端没授权时启动链路不能走到资源加载：useRemoteService 会自己发起授权请求并等到超时，
 * 失败还被报成「资源加载失败」，把用户往重新初始化资源上引
 */
class StartRequiresBackendGrantContractTest {

    private val composition = TestSources.resolve(
        "src/main/java/com/aliothmoon/maameow/domain/service/MaaCompositionService.kt"
    ).readText()

    @Test
    fun prepareResources_skipsLoadingWhenNotGranted() {
        val body = composition.substringAfter("suspend fun prepareResources(")
            .substringBefore("private suspend fun checkPreconditions(")
        assertBefore(body, "access.isGranted(", "resourceLoader.ensureLoaded(")
    }

    @Test
    fun checkPreconditions_rejectsNotGrantedBeforeLoading() {
        val body = composition.substringAfter("private suspend fun checkPreconditions(")
            .substringBefore("private suspend fun ensureMaaInstance(")
        assertBefore(body, "\"BACKEND_NOT_GRANTED\"", "resourceLoader.ensureLoaded(")
        // 服务没在运行时谈不上授权，先报不可用
        assertBefore(body, "\"BACKEND_UNAVAILABLE\"", "\"BACKEND_NOT_GRANTED\"")
    }

    @Test
    fun notGranted_isNotReportedAsFault() {
        val reported = TestSources.resolve(
            "src/main/java/com/aliothmoon/maameow/telemetry/TelemetryController.kt"
        ).readText()
            .substringAfter("val REPORTED_START_FAILURES = setOf(")
            .substringBefore(")")
        assertTrue(reported.contains("\"RESOURCE_ERROR\""))
        assertFalse("未授权是用户侧条件，不上报", reported.contains("BACKEND_NOT_GRANTED"))
    }

    private fun assertBefore(body: String, first: String, second: String) {
        val firstIndex = body.indexOf(first)
        val secondIndex = body.indexOf(second)
        assertTrue("找不到 $first", firstIndex >= 0)
        assertTrue("找不到 $second", secondIndex >= 0)
        assertTrue("$first 必须排在 $second 之前", firstIndex < secondIndex)
    }
}
