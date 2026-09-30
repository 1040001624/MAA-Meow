package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.presentation.components.LocalPageVisible
import com.aliothmoon.maameow.presentation.components.collectWhilePageVisible
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogPanelAutoScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 会话日志满上限后从头丢旧条，条数不变，仍要跟到最新一条 */
    @Test
    fun keepsFollowingAfterLogCap() {
        val all = List(CAP + 50) { LogItem(content = "log-$it") }
        var logs by mutableStateOf(all.subList(0, CAP).toList())

        composeRule.setContent {
            LogPanel(logs = logs, onClearLogs = {})
        }
        composeRule.onNodeWithText("log-${CAP - 1}").assertIsDisplayed()

        composeRule.runOnIdle { logs = all.subList(50, CAP + 50).toList() }
        composeRule.onNodeWithText("log-${CAP + 49}").assertIsDisplayed()
    }

    @Test
    fun hiddenPageStopsCollecting() {
        val source = MutableStateFlow(0)
        val visible = mutableStateOf(false)

        composeRule.setContent {
            CompositionLocalProvider(LocalPageVisible provides visible) {
                val value by source.collectWhilePageVisible()
                Text("value=$value")
            }
        }
        composeRule.onNodeWithText("value=0").assertExists()

        composeRule.runOnIdle { source.value = 1 }
        composeRule.onNodeWithText("value=0").assertExists()

        composeRule.runOnIdle { visible.value = true }
        composeRule.onNodeWithText("value=1").assertExists()
    }

    private companion object {
        const val CAP = 750
    }
}
