package com.aliothmoon.maameow.schedule.ui

import android.widget.Toast
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.domain.models.UnlockStep
import com.aliothmoon.maameow.presentation.components.CollapsibleSection
import com.aliothmoon.maameow.presentation.components.ListItemDivider
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.view.settings.SettingSecretField
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.utils.i18n.resolve
import org.koin.androidx.compose.koinViewModel

@Composable
fun ScheduleWakeUnlockView(
    navController: NavController,
    viewModel: ScheduleWakeUnlockViewModel = koinViewModel(),
) {
    val wakeUnlockType by viewModel.wakeUnlockType.collectAsStateWithLifecycle()
    val wakeCredential by viewModel.wakeCredential.collectAsStateWithLifecycle()
    val wakeTestState by viewModel.wakeTestState.collectAsStateWithLifecycle()
    val unlockGesture by viewModel.unlockGesture.collectAsStateWithLifecycle()
    val gestureRecordState by viewModel.gestureRecordState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    wakeTestState?.let { state ->
        LaunchedEffect(state) {
            val done = state as? ScheduleWakeUnlockViewModel.WakeTestState.Done
                ?: return@LaunchedEffect
            Toast.makeText(
                context,
                done.result.message.resolve(context),
                Toast.LENGTH_LONG,
            ).show()
            viewModel.clearWakeTestResult()
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshGestureRecord() }

    gestureRecordState?.let { state ->
        val doneTemplate = stringResource(R.string.settings_wake_gesture_done)
        LaunchedEffect(state) {
            val message = when (state) {
                is ScheduleWakeUnlockViewModel.GestureRecordState.Done ->
                    doneTemplate.format(state.steps)

                is ScheduleWakeUnlockViewModel.GestureRecordState.Failed ->
                    state.result.message.resolve(context)

                else -> return@LaunchedEffect
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearGestureRecordState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.schedule_wake_unlock_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() },
            )
        },
    ) { paddingValues ->
        val contentColor = MaterialTheme.colorScheme.onSurface

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            contentPadding = PaddingValues(
                horizontal = MaaDesignTokens.Spacing.listHorizontal,
                vertical = MaaDesignTokens.Spacing.sm,
            ),
        ) {
            item {
                Text(
                    text = stringResource(R.string.schedule_wake_unlock_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = MaaDesignTokens.Spacing.sm),
                )
                SettingsGroupCard {
                    SettingWakeUnlockTypeItem(
                        contentColor = contentColor,
                        selectedType = wakeUnlockType,
                        onTypeSelected = { viewModel.setWakeUnlockType(it) },
                    )
                    MaaAnimatedVisibility(
                        visible = wakeUnlockType == AppSettingsManager.WAKE_TYPE_PIN,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            ListItemDivider()
                            SettingWakePinSection(
                                contentColor = contentColor,
                                wakeCredential = wakeCredential,
                                onCredentialChange = { viewModel.setWakeCredential(it) },
                                onTest = { viewModel.runWakeTest() },
                            )
                        }
                    }
                    MaaAnimatedVisibility(
                        visible = wakeUnlockType == AppSettingsManager.WAKE_TYPE_GESTURE,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            ListItemDivider()
                            SettingWakeGestureSection(
                                contentColor = contentColor,
                                gesture = unlockGesture,
                                recordState = gestureRecordState,
                                onRecord = { viewModel.startGestureRecord() },
                                onCancelRecord = { viewModel.cancelGestureRecord() },
                                onClear = { viewModel.clearGesture() },
                                onTest = { viewModel.runWakeTest() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingWakeUnlockTypeItem(
    contentColor: Color,
    selectedType: String,
    onTypeSelected: (String) -> Unit,
) {
    // 三个选项字宽差得多，单选圆点排一行会左右不齐
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_wake_unlock_type),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
        val options = listOf(
            AppSettingsManager.WAKE_TYPE_SWIPE to
                    stringResource(R.string.settings_wake_unlock_type_swipe),
            AppSettingsManager.WAKE_TYPE_GESTURE to
                    stringResource(R.string.settings_wake_unlock_type_gesture),
            AppSettingsManager.WAKE_TYPE_PIN to
                    stringResource(R.string.settings_wake_unlock_type_pin),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (type, label) ->
                SegmentedButton(
                    selected = type == selectedType,
                    onClick = { onTypeSelected(type) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                        baseShape = RoundedCornerShape(4.dp),
                    ),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingWakeGestureSection(
    contentColor: Color,
    gesture: UnlockGesture?,
    recordState: ScheduleWakeUnlockViewModel.GestureRecordState?,
    onRecord: () -> Unit,
    onCancelRecord: () -> Unit,
    onClear: () -> Unit,
    onTest: () -> Unit,
) {
    val recording = recordState is ScheduleWakeUnlockViewModel.GestureRecordState.Preparing ||
            recordState is ScheduleWakeUnlockViewModel.GestureRecordState.Recording

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (gesture == null) {
            Text(
                text = stringResource(R.string.settings_wake_gesture_none),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
        } else {
            // 步骤只在需要核对时才看，默认收起
            CollapsibleSection(
                sectionKey = "settings_wake_gesture_steps",
                initiallyExpanded = false,
                title = {
                    Text(
                        text = stringResource(
                            R.string.settings_wake_gesture_summary,
                            gesture.steps.size,
                            gesture.screenWidth,
                            gesture.screenHeight,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                        modifier = Modifier.weight(1f),
                    )
                },
            ) {
                gesture.steps.forEachIndexed { index, step ->
                    GestureStepRow(index = index, step = step, contentColor = contentColor)
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_wake_gesture_hint),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.7f),
        )

        if (recording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(
                        if (recordState is ScheduleWakeUnlockViewModel.GestureRecordState.Preparing) {
                            R.string.settings_wake_gesture_preparing
                        } else {
                            R.string.settings_wake_gesture_waiting
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
            OutlinedButton(
                onClick = onCancelRecord,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.settings_wake_gesture_cancel))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRecord,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (gesture == null) {
                                R.string.settings_wake_gesture_record
                            } else {
                                R.string.settings_wake_gesture_rerecord
                            },
                        ),
                    )
                }
                if (gesture != null) {
                    OutlinedButton(
                        onClick = onClear,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.settings_wake_gesture_clear))
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_wake_gesture_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        if (gesture != null) {
            SettingRow(
                title = stringResource(R.string.settings_wake_test_button),
                description = stringResource(R.string.settings_wake_gesture_test_hint),
                titleColor = contentColor,
                descriptionColor = contentColor.copy(alpha = 0.7f),
                onClick = onTest,
            )
        }
    }
}

@Composable
private fun GestureStepRow(
    index: Int,
    step: UnlockStep,
    contentColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.6f),
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gestureStepText(step),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            if (step.delayBeforeMs > 0) {
                Text(
                    text = stringResource(
                        R.string.settings_wake_gesture_step_delay,
                        step.delayBeforeMs,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun gestureStepText(step: UnlockStep): String = when (step) {
    is UnlockStep.Tap ->
        stringResource(R.string.settings_wake_gesture_step_tap, step.x, step.y)

    is UnlockStep.LongPress ->
        stringResource(
            R.string.settings_wake_gesture_step_long_press,
            step.x,
            step.y,
            step.holdMs,
        )

    is UnlockStep.Swipe -> {
        val first = step.points.firstOrNull()
        val last = step.points.lastOrNull()
        stringResource(
            R.string.settings_wake_gesture_step_swipe,
            first?.x ?: 0,
            first?.y ?: 0,
            last?.x ?: 0,
            last?.y ?: 0,
            last?.tMs ?: 0,
        )
    }
}

@Composable
private fun SettingWakePinSection(
    contentColor: Color,
    wakeCredential: String,
    onCredentialChange: (String) -> Unit,
    onTest: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        SettingSecretField(
            value = wakeCredential,
            label = stringResource(R.string.settings_wake_credential),
            placeholder = stringResource(R.string.settings_wake_credential_hint),
            keyboardType = KeyboardType.NumberPassword,
            onValueChange = onCredentialChange,
            transform = { it.filter(Char::isDigit).take(AppSettingsManager.MAX_PIN_LENGTH) },
        )
        Text(
            text = stringResource(R.string.settings_wake_credential_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        SettingRow(
            title = stringResource(R.string.settings_wake_test_button),
            description = stringResource(R.string.settings_wake_test_hint),
            titleColor = contentColor,
            descriptionColor = contentColor.copy(alpha = 0.7f),
            onClick = onTest,
        )
    }
}
