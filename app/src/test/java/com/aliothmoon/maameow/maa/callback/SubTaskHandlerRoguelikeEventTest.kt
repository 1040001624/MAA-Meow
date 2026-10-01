package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/** 肉鸽事件回调：事件名、选项清单与上游 v6.19.0-beta.1 新增的实际选择，对齐 WPF RoguelikeSettingsUserControlModel */
class SubTaskHandlerRoguelikeEventTest {

    private val pkg = "com.aliothmoon.maameow"
    private val resources: Resources = mockk()
    private val context: Context = mockk {
        every { resources } returns this@SubTaskHandlerRoguelikeEventTest.resources
        every { packageName } returns pkg
    }
    private val sessionLogger: MaaSessionLogger = mockk(relaxed = true)

    private val handler = SubTaskHandler(
        applicationContext = context,
        statusTracker = mockk(relaxed = true),
        sessionLogger = sessionLogger,
        copilotRuntimeStateStore = mockk(relaxed = true),
        resourceDataManager = mockk<ResourceDataManager>(relaxed = true),
        toolboxResultCollector = mockk(relaxed = true),
        notificationCenter = mockk<MaaNotificationCenter>(relaxed = true),
        chainState = mockk<TaskChainState>(relaxed = true),
        activityManager = mockk<ActivityManager>(relaxed = true),
        achievementRepository = mockk<AchievementRepository>(relaxed = true),
        depotRepository = mockk<DepotRepository>(relaxed = true),
    )

    private fun stub(name: String, id: Int, template: String) {
        every { resources.getIdentifier(name, "string", pkg) } returns id
        every { resources.getString(id) } returns template
        every { resources.getString(id, *anyVararg()) } answers {
            secondArg<Array<Any>>().foldIndexed(template) { i, acc, arg -> acc.replace("{$i}", arg.toString()) }
        }
    }

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        every { resources.getIdentifier(any(), "string", pkg) } returns 0
        stub("maa_roguelike_event", 1, "事件:")
        stub("maa_roguelike_encounter_options", 2, "识别到 {0} 个选项:")
        stub("maa_roguelike_encounter_enabled_option", 3, "可选选项: {0}")
        stub("maa_roguelike_encounter_disabled_option", 4, "不可选选项: {0}")
        stub("maa_roguelike_event_selected", 5, "已选择: {0}")
        stub("maa_roguelike_event_selected_with_reason", 6, "已选择: {0}（{1}）")
        stub("maa_roguelike_event_selected_fallback", 7, "目标选项不可用，兜底选择: {0}")
    }

    private fun extra(what: String, details: JSONObject) = handler.onSubTaskExtraInfo(
        JSONObject.of("taskchain", "Roguelike", "what", what, "details", details)
    )

    @Test
    fun event_logsName() {
        extra("RoguelikeEvent", JSONObject.of("name", "秘境行商"))
        verify { sessionLogger.append("事件: 秘境行商", LogLevel.INFO) }
    }

    @Test
    fun encounterOptions_listsEnabledAndDisabled() {
        val options = JSONArray.of(
            JSONObject.of("text", "接受交易", "enabled", true),
            JSONObject.of("text", "强行夺取", "enabled", false),
        )
        extra("RoguelikeEncounterOptions", JSONObject.of("options", options))
        verify {
            sessionLogger.append("识别到 2 个选项:\n可选选项: 接受交易\n不可选选项: 强行夺取", LogLevel.INFO)
        }
    }

    @Test
    fun selected_withRuleDescription_showsReason() {
        extra(
            "RoguelikeEventSelected",
            JSONObject.of("option_text", "接受交易", "rule_description", "优先拿源石锭", "used_fallback", false),
        )
        verify { sessionLogger.append("已选择: 接受交易（优先拿源石锭）", LogLevel.INFO) }
    }

    @Test
    fun selected_withoutRuleDescription_showsOptionOnly() {
        extra("RoguelikeEventSelected", JSONObject.of("option_text", "离开", "rule_description", ""))
        verify { sessionLogger.append("已选择: 离开", LogLevel.INFO) }
    }

    @Test
    fun selected_fallback_ignoresReason() {
        extra(
            "RoguelikeEventSelected",
            JSONObject.of("option_text", "离开", "rule_description", "优先拿源石锭", "used_fallback", true),
        )
        verify { sessionLogger.append("目标选项不可用，兜底选择: 离开", LogLevel.INFO) }
    }
}
