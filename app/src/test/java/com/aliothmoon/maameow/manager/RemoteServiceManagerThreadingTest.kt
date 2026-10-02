package com.aliothmoon.maameow.manager

import com.aliothmoon.maameow.RemoteService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Test

/** useRemoteService 的 action 不得落在调用方线程：主线程上的同步 Binder 调用会 ANR */
class RemoteServiceManagerThreadingTest {

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun actionRunsOffCallerThread() = runBlocking {
        // RemoteAccessCoordinator 初始化就会取快照，两个后端得先于它桩掉
        mockkObject(ShizukuManager, RootManager)
        every { ShizukuManager.isAvailable() } returns true
        every { ShizukuManager.isGranted() } returns true
        every { RootManager.isAvailable() } returns false
        every { RootManager.isGranted() } returns false
        mockkObject(RemoteServiceManager)
        coEvery { RemoteServiceManager.getInstance(any()) } returns mockk<RemoteService>()

        val caller = Thread.currentThread()
        val actionThread = RemoteServiceManager.useRemoteService { Thread.currentThread() }

        assertNotSame(caller, actionThread)
    }
}
