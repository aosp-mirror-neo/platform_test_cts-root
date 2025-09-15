/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.input.cts

import android.hardware.input.InputManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.server.wm.CtsWindowInfoUtils.waitForWindowOnTop
import android.view.MotionEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.UinputTouchPad
import com.android.input.flags.Flags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the round-trip configuration of input devices. Device configuration files for different
 * names are pushed to the device from `data` using a `PushFilePreparer`. Corresponding
 * virtual input devices are then created in tests to validate the configurations.
 */
@RunWith(AndroidJUnit4::class)
class DeviceConfigurationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var activity: CaptureEventActivity
    private lateinit var inputManager: InputManager

    @get:Rule val activityRule = ActivityScenarioRule(CaptureEventActivity::class.java)

    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setUp() {
        activityRule.scenario.onActivity { activity = it }
        inputManager = activity.getSystemService(InputManager::class.java)
        assertTrue(
            "Failed to wait for activity window to be on top",
            waitForWindowOnTop(activity.window),
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API,
        Flags.FLAG_INPUT_DEVICE_PRIMARY_DIRECTIONAL_MOTION_AXIS_API,
    )
    @Test
    fun testPrimaryDirectionalMotionAxisNotSpecified() {
        assertPrimaryDirectionalMotionAxis(
            "TestTouchpadWithEmptyConfiguration",
            null,
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API,
        Flags.FLAG_INPUT_DEVICE_PRIMARY_DIRECTIONAL_MOTION_AXIS_API,
    )
    @Test
    fun testPrimaryDirectionalMotionAxisEmpty() {
        assertPrimaryDirectionalMotionAxis(
            "TestTouchpadWithEmptyPrimaryDirectionalMotionAxis",
            null,
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API,
        Flags.FLAG_INPUT_DEVICE_PRIMARY_DIRECTIONAL_MOTION_AXIS_API,
    )
    @Test
    fun testPrimaryDirectionalMotionAxisInvalid() {
        assertPrimaryDirectionalMotionAxis(
            "TestTouchpadWithInvalidPrimaryDirectionalMotionAxis",
            null,
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API,
        Flags.FLAG_INPUT_DEVICE_PRIMARY_DIRECTIONAL_MOTION_AXIS_API,
    )
    @Test
    fun testPrimaryDirectionalMotionAxisMultiple() {
        assertPrimaryDirectionalMotionAxis(
            "TestTouchpadWithMultiplePrimaryDirectionalMotionAxis",
            null,
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API,
        Flags.FLAG_INPUT_DEVICE_PRIMARY_DIRECTIONAL_MOTION_AXIS_API,
    )
    @Test
    fun testPrimaryDirectionalMotionAxisX() {
        assertPrimaryDirectionalMotionAxis(
            "TestTouchpadWithPrimaryDirectionalMotionAxisX",
            MotionEvent.AXIS_X,
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API,
        Flags.FLAG_INPUT_DEVICE_PRIMARY_DIRECTIONAL_MOTION_AXIS_API,
    )
    @Test
    fun testPrimaryDirectionalMotionAxisY() {
        assertPrimaryDirectionalMotionAxis(
            "TestTouchpadWithPrimaryDirectionalMotionAxisY",
            MotionEvent.AXIS_Y,
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API,
        Flags.FLAG_INPUT_DEVICE_PRIMARY_DIRECTIONAL_MOTION_AXIS_API,
    )
    @Test
    fun testPrimaryDirectionalMotionAxisZ() {
        assertPrimaryDirectionalMotionAxis(
            "TestTouchpadWithPrimaryDirectionalMotionAxisZ",
            MotionEvent.AXIS_Z,
        )
    }

    fun assertPrimaryDirectionalMotionAxis(
        touchpadName: String,
        expectedAxis: Int?,
    ) {
        UinputTouchPad(
                instrumentation,
                activity.display,
                name = touchpadName,
            )
            .use { touchpad ->
                val viewBehavior = inputManager!!.getInputDeviceViewBehavior(touchpad.getDeviceId())
                assertNotNull(viewBehavior)
                if (expectedAxis == null) {
                    assertFalse(viewBehavior!!.hasPrimaryDirectionalMotionAxis())
                    assertThrows(IllegalStateException::class.java) {
                        viewBehavior!!.primaryDirectionalMotionAxis
                    }
                } else {
                    assertTrue(viewBehavior!!.hasPrimaryDirectionalMotionAxis())
                    assertEquals(expectedAxis, viewBehavior!!.primaryDirectionalMotionAxis)
                }
            }
    }
}
