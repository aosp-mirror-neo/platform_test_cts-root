/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.permission.cts_root

import android.Manifest
import android.app.AppOpsManager
import android.content.pm.PackageManagerInternal
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.permission.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.SparseIntArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.android.server.LocalManagerRegistry
import com.android.server.LocalServices
import com.android.server.SystemConfig
import com.android.server.permission.PermissionBpfMap
import com.android.server.permission.PermissionManagerLocal
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.CINNAMON_BUN, codeName = "CinnamonBun")
@RequiresFlagsEnabled(Flags.FLAG_ACCESS_LOCAL_NETWORK_PERMISSION_ENABLED)
class PermissionBpfMapTest {
    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()!!

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

    private val permissionManagerLocal =
        LocalManagerRegistry.getManager(PermissionManagerLocal::class.java)!!
    private val packageManagerInternal =
        LocalServices.getService(PackageManagerInternal::class.java)!!
    private val packageManager = context.packageManager
    private val userManager = context.getSystemService(UserManager::class.java)!!
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!

    private var testApp1Uid: Int = -1
    private var testApp2Uid: Int = -1

    private class TestPermissionBpfMap(
        private val callbackInvocations: MutableList<String>,
        var latch: CountDownLatch,
    ) : PermissionBpfMap {
        var uidsPermissionBitsArray = SparseIntArray()
        var removedUserId = -1
        var removedAppId = -1

        override fun setUidsPermissionBits(uidsPermissionBits: SparseIntArray) {
            if (latch.count == 0L) {
                return
            }
            callbackInvocations.add(SET_UIDS_PERMISSIONS_CALLED)
            uidsPermissionBitsArray = uidsPermissionBits
            latch.countDown()
        }

        override fun removeAppId(appId: Int) {
            callbackInvocations.add(REMOVE_APP_ID_CALLED)
            removedAppId = appId
            latch.countDown()
        }

        override fun removeUser(userId: Int) {
            callbackInvocations.add(REMOVE_USER_CALLED)
            removedUserId = userId
            latch.countDown()
        }
    }

    private fun isNthBitSet(flags: Int, bitIndexToCheck: Int) =
        (flags and (1 shl bitIndexToCheck)) != 0

    private fun isPermissionBitSet(
        permissionBpfMap: TestPermissionBpfMap,
        uid: Int,
        bitIndex: Int,
    ): Boolean {
        assertWithMessage("UID $uid not found in permission BPF map")
            .that(permissionBpfMap.uidsPermissionBitsArray.indexOfKey(uid) >= 0)
            .isTrue()
        return isNthBitSet(permissionBpfMap.uidsPermissionBitsArray.get(uid), bitIndex)
    }

    private fun getAllGrantedPermissionsBits(permissionNames: List<String>) =
        ((1L shl permissionNames.size) - 1).toInt()

    private fun awaitLatch(latch: CountDownLatch) =
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()

    @Before
    fun setup() {
        testApp1Uid = packageManager.getPackageInfo(TEST_APP_PACKAGE_1, 0).applicationInfo!!.uid
        testApp2Uid = packageManager.getPackageInfo(TEST_APP_PACKAGE_2, 0).applicationInfo!!.uid
    }

    @Test
    fun registerBpfMap_withUnsupportedPermission_throwsIllegalArgumentException() {
        val testPermissionBpfMap = TestPermissionBpfMap(mutableListOf(), CountDownLatch(1))
        assertThrows(IllegalArgumentException::class.java) {
            permissionManagerLocal.registerBpfMap(
                testPermissionBpfMap,
                SUPPORTED_BPF_PERMISSIONS + UNSUPPORTED_BPF_PERMISSION,
            )
        }
    }

    @Test
    fun registerBpfMap_withEmptyPermissions_throwsIllegalArgumentException() {
        val testPermissionBpfMap = TestPermissionBpfMap(mutableListOf(), CountDownLatch(1))
        assertThrows(IllegalArgumentException::class.java) {
            permissionManagerLocal.registerBpfMap(testPermissionBpfMap, emptyList())
        }
    }

    @Test
    fun registerBpfMap_givenTooManyPermissions_throwsIllegalArgumentException() {
        val testPermissionBpfMap = TestPermissionBpfMap(mutableListOf(), CountDownLatch(1))
        val permissions = List(Int.SIZE_BITS + 1) { PERMISSION_ACCESS_LOCAL_NETWORK }
        assertThrows(IllegalArgumentException::class.java) {
            permissionManagerLocal.registerBpfMap(testPermissionBpfMap, permissions)
        }
    }

    @Test
    fun removingUser_triggers_removeUserCallback() {
        val callbackInvocations = mutableListOf<String>()
        val testPermissionBpfMap = TestPermissionBpfMap(callbackInvocations, CountDownLatch(1))

        var userId = -1
        try {
            val userInfo = userManager.createUser("TestUser", UserManager.USER_TYPE_FULL_GUEST, 0)!!
            userId = userInfo.userHandle.identifier

            permissionManagerLocal.registerBpfMap(testPermissionBpfMap, SUPPORTED_BPF_PERMISSIONS)
            awaitLatch(testPermissionBpfMap.latch)

            testPermissionBpfMap.latch = CountDownLatch(1)
            userManager.removeUser(userId)
            awaitLatch(testPermissionBpfMap.latch)

            assertThat(callbackInvocations).contains(REMOVE_USER_CALLED)
            assertThat(testPermissionBpfMap.removedUserId).isEqualTo(userId)
        } finally {
            permissionManagerLocal.unregisterBpfMap(testPermissionBpfMap)
            userManager.removeUser(userId)
        }
    }

    @Test
    fun uninstallingApp_triggers_removeAppIdCallback() {
        val callbackInvocations = mutableListOf<String>()
        val testPermissionBpfMap = TestPermissionBpfMap(callbackInvocations, CountDownLatch(1))

        try {
            permissionManagerLocal.registerBpfMap(testPermissionBpfMap, SUPPORTED_BPF_PERMISSIONS)
            awaitLatch(testPermissionBpfMap.latch)

            testPermissionBpfMap.latch = CountDownLatch(1)
            SystemUtil.runShellCommandOrThrow("pm uninstall $TEST_APP_PACKAGE_1")
            awaitLatch(testPermissionBpfMap.latch)

            assertThat(callbackInvocations).contains(REMOVE_APP_ID_CALLED)
            assertThat(testPermissionBpfMap.removedAppId).isEqualTo(testApp1Uid)
        } finally {
            permissionManagerLocal.unregisterBpfMap(testPermissionBpfMap)
            SystemUtil.runShellCommandOrThrow("pm install $TEST_APP_APK_1")
        }
    }

    @Test
    fun registerBpfMap_triggers_setUidsPermissionBitsCallback_initially() {
        val testPermissionBpfMap = TestPermissionBpfMap(mutableListOf(), CountDownLatch(1))

        try {
            permissionManagerLocal.registerBpfMap(testPermissionBpfMap, SUPPORTED_BPF_PERMISSIONS)
            awaitLatch(testPermissionBpfMap.latch)

            assertWithMessage("Initial callback should have at least 4 uids")
                .that(testPermissionBpfMap.uidsPermissionBitsArray.size())
                .isGreaterThan(3)
            assertThat(testPermissionBpfMap.uidsPermissionBitsArray.indexOfKey(testApp1Uid) >= 0)
                .isTrue()
            assertThat(testPermissionBpfMap.uidsPermissionBitsArray.indexOfKey(testApp2Uid) >= 0)
                .isTrue()

            assertRootAndSystemUid(
                context.userId,
                testPermissionBpfMap.uidsPermissionBitsArray,
                SUPPORTED_BPF_PERMISSIONS,
            )
            assertSystemConfigUids(
                context.userId,
                testPermissionBpfMap.uidsPermissionBitsArray,
                SUPPORTED_BPF_PERMISSIONS,
            )
        } finally {
            permissionManagerLocal.unregisterBpfMap(testPermissionBpfMap)
        }
    }

    @Test
    fun addingUser_triggers_setUidsPermissionBitsCallback() {
        val testPermissionBpfMap = TestPermissionBpfMap(mutableListOf(), CountDownLatch(1))

        var userId = -1
        try {
            permissionManagerLocal.registerBpfMap(testPermissionBpfMap, SUPPORTED_BPF_PERMISSIONS)
            awaitLatch(testPermissionBpfMap.latch)

            testPermissionBpfMap.latch = CountDownLatch(1)

            val userInfo = userManager.createUser("TestUser", UserManager.USER_TYPE_FULL_GUEST, 0)!!
            userId = userInfo.userHandle.identifier

            awaitLatch(testPermissionBpfMap.latch)

            assertWithMessage("Callback should have at least 2 uids")
                .that(testPermissionBpfMap.uidsPermissionBitsArray.size())
                .isGreaterThan(1)

            assertRootAndSystemUid(
                userId,
                testPermissionBpfMap.uidsPermissionBitsArray,
                SUPPORTED_BPF_PERMISSIONS,
            )
            assertSystemConfigUids(
                userId,
                testPermissionBpfMap.uidsPermissionBitsArray,
                SUPPORTED_BPF_PERMISSIONS,
            )
        } finally {
            permissionManagerLocal.unregisterBpfMap(testPermissionBpfMap)
            userManager.removeUser(userId)
        }
    }

    private fun assertRootAndSystemUid(
        userId: Int,
        uidsPermissionBitsArray: SparseIntArray,
        permissionNames: List<String>,
    ) {
        val rootUid = UserHandle.getUid(userId, Process.ROOT_UID)
        assertThat(uidsPermissionBitsArray.indexOfKey(rootUid) >= 0).isTrue()
        assertThat(uidsPermissionBitsArray.get(rootUid))
            .isEqualTo(getAllGrantedPermissionsBits(permissionNames))

        val systemUid = UserHandle.getUid(userId, Process.SYSTEM_UID)
        assertThat(uidsPermissionBitsArray.indexOfKey(systemUid) >= 0).isTrue()
        assertThat(uidsPermissionBitsArray.get(systemUid))
            .isEqualTo(getAllGrantedPermissionsBits(permissionNames))
    }

    private fun assertSystemConfigUids(
        userId: Int,
        uidsPermissionBitsArray: SparseIntArray,
        permissionNames: List<String>,
    ) {
        val systemPermissions = SystemConfig.getInstance().systemPermissions
        for (i in 0 until systemPermissions.size()) {
            val appId = systemPermissions.keyAt(i)
            val targetUid = UserHandle.getUid(userId, appId)
            if (packageManagerInternal.getPackage(targetUid) != null) {
                continue
            }

            val actualBits = uidsPermissionBitsArray.get(targetUid, 0)
            permissionNames.forEachIndexed { index, permissionName ->
                val shouldBeGranted = permissionName in systemPermissions.valueAt(i)
                val isBitSet = (actualBits and (1 shl index)) != 0
                val expectedState = if (shouldBeGranted) "set" else "unset"
                assertWithMessage(
                        "Permission $permissionName should be $expectedState for UID $targetUid" +
                            " (appId=$appId)"
                    )
                    .that(isBitSet)
                    .isEqualTo(shouldBeGranted)
            }
        }
    }

    @Test
    fun permissionChange_triggers_setUidsPermissionBitsCallback_inCorrectBitOrder() {
        val permissionUnderTest = PERMISSION_ACCESS_LOCAL_NETWORK
        val permissionsOrderList = buildList {
            add(listOf(permissionUnderTest, PERMISSION_UPDATE_DEVICE_STATS, PERMISSION_INTERNET))
            add(listOf(PERMISSION_UPDATE_DEVICE_STATS, permissionUnderTest, PERMISSION_INTERNET))
            add(listOf(PERMISSION_UPDATE_DEVICE_STATS, PERMISSION_INTERNET, permissionUnderTest))
        }

        permissionsOrderList.forEach { permissionsOrder ->
            val indexOfPermissionUnderTest = permissionsOrder.indexOf(permissionUnderTest)
            val testPermissionBpfMap = TestPermissionBpfMap(mutableListOf(), CountDownLatch(1))
            try {
                permissionManagerLocal.registerBpfMap(testPermissionBpfMap, permissionsOrder)
                awaitLatch(testPermissionBpfMap.latch)

                testPermissionBpfMap.latch = CountDownLatch(1)
                uiAutomation.grantRuntimePermission(TEST_APP_PACKAGE_1, permissionUnderTest)
                awaitLatch(testPermissionBpfMap.latch)
                assertWithMessage("Permission bit must be set at index $indexOfPermissionUnderTest")
                    .that(
                        isPermissionBitSet(
                            testPermissionBpfMap,
                            testApp1Uid,
                            indexOfPermissionUnderTest,
                        )
                    )
                    .isTrue()

                testPermissionBpfMap.latch = CountDownLatch(1)
                uiAutomation.revokeRuntimePermission(TEST_APP_PACKAGE_1, permissionUnderTest)
                awaitLatch(testPermissionBpfMap.latch)
                assertWithMessage(
                        "Permission bit must be unset at index $indexOfPermissionUnderTest"
                    )
                    .that(
                        isPermissionBitSet(
                            testPermissionBpfMap,
                            testApp1Uid,
                            indexOfPermissionUnderTest,
                        )
                    )
                    .isFalse()
            } finally {
                uiAutomation.revokeRuntimePermission(TEST_APP_PACKAGE_1, permissionUnderTest)
                permissionManagerLocal.unregisterBpfMap(testPermissionBpfMap)
            }
        }
    }

    @Test
    fun appOpsModeChange_triggers_setUidsPermissionBitsCallback() {
        val permissionUnderTest = PERMISSION_ACCESS_LOCAL_NETWORK
        val testPermissionBpfMap = TestPermissionBpfMap(mutableListOf(), CountDownLatch(1))
        val indexOfPermissionUnderTest = SUPPORTED_BPF_PERMISSIONS.indexOf(permissionUnderTest)

        try {
            uiAutomation.grantRuntimePermission(TEST_APP_PACKAGE_1, permissionUnderTest)
            appOpsManager.setUidMode(
                AppOpsManager.OPSTR_ACCESS_LOCAL_NETWORK,
                testApp1Uid,
                AppOpsManager.MODE_IGNORED,
            )

            permissionManagerLocal.registerBpfMap(testPermissionBpfMap, SUPPORTED_BPF_PERMISSIONS)
            awaitLatch(testPermissionBpfMap.latch)
            assertWithMessage(
                    "Permission bit must be unset when the AppOps mode is ignored initially"
                )
                .that(
                    isPermissionBitSet(
                        testPermissionBpfMap,
                        testApp1Uid,
                        indexOfPermissionUnderTest,
                    )
                )
                .isFalse()

            testPermissionBpfMap.latch = CountDownLatch(1)
            appOpsManager.setUidMode(
                AppOpsManager.OPSTR_ACCESS_LOCAL_NETWORK,
                testApp1Uid,
                AppOpsManager.MODE_ALLOWED,
            )
            awaitLatch(testPermissionBpfMap.latch)
            assertWithMessage("Permission bit must be set when AppOps mode is changed to allowed")
                .that(
                    isPermissionBitSet(
                        testPermissionBpfMap,
                        testApp1Uid,
                        indexOfPermissionUnderTest,
                    )
                )
                .isTrue()

            testPermissionBpfMap.latch = CountDownLatch(1)
            appOpsManager.setUidMode(
                AppOpsManager.OPSTR_ACCESS_LOCAL_NETWORK,
                testApp1Uid,
                AppOpsManager.MODE_IGNORED,
            )
            awaitLatch(testPermissionBpfMap.latch)
            assertWithMessage(
                    "Permission bit must be unset when the AppOps mode is changed to ignored"
                )
                .that(
                    isPermissionBitSet(
                        testPermissionBpfMap,
                        testApp1Uid,
                        indexOfPermissionUnderTest,
                    )
                )
                .isFalse()
        } finally {
            testPermissionBpfMap.latch = CountDownLatch(1)
            appOpsManager.setUidMode(
                AppOpsManager.OPSTR_ACCESS_LOCAL_NETWORK,
                testApp1Uid,
                AppOpsManager.MODE_ALLOWED,
            )
            uiAutomation.revokeRuntimePermission(TEST_APP_PACKAGE_1, permissionUnderTest)
            testPermissionBpfMap.latch.await(2, TimeUnit.SECONDS)
            permissionManagerLocal.unregisterBpfMap(testPermissionBpfMap)
        }
    }

    @Test
    fun unregisterBpfMap_stopsCallbacks() {
        val callbackInvocations = mutableListOf<String>()
        val testPermissionBpfMap = TestPermissionBpfMap(callbackInvocations, CountDownLatch(1))

        try {
            permissionManagerLocal.registerBpfMap(testPermissionBpfMap, SUPPORTED_BPF_PERMISSIONS)
            awaitLatch(testPermissionBpfMap.latch)

            permissionManagerLocal.unregisterBpfMap(testPermissionBpfMap)

            testPermissionBpfMap.latch = CountDownLatch(1)
            callbackInvocations.clear()
            uiAutomation.grantRuntimePermission(TEST_APP_PACKAGE_1, PERMISSION_ACCESS_LOCAL_NETWORK)

            assertWithMessage("Callbacks should not be invoked for an unregistered BPF map")
                .that(testPermissionBpfMap.latch.await(2, TimeUnit.SECONDS))
                .isFalse()
            assertThat(callbackInvocations).isEmpty()
        } finally {
            testPermissionBpfMap.latch = CountDownLatch(1)
            uiAutomation.revokeRuntimePermission(
                TEST_APP_PACKAGE_1,
                PERMISSION_ACCESS_LOCAL_NETWORK,
            )
            testPermissionBpfMap.latch.await(2, TimeUnit.SECONDS)
            permissionManagerLocal.unregisterBpfMap(testPermissionBpfMap)
        }
    }

    companion object {
        private const val TEST_APP_PACKAGE_1 = "android.permission.cts_root.permission.bpf.map.app1"
        private const val TEST_APP_PACKAGE_2 = "android.permission.cts_root.permission.bpf.map.app2"
        private const val TEST_APP_APK_1 =
            "/data/local/tmp/cts-root-permission/CtsRootPermissionBpfMapApp1.apk"

        private const val SET_UIDS_PERMISSIONS_CALLED = "setUidsPermissions"
        private const val REMOVE_APP_ID_CALLED = "removeAppId"
        private const val REMOVE_USER_CALLED = "removeUser"

        private const val UNSUPPORTED_BPF_PERMISSION = Manifest.permission.RECORD_AUDIO
        private const val PERMISSION_ACCESS_LOCAL_NETWORK = Manifest.permission.ACCESS_LOCAL_NETWORK
        private const val PERMISSION_UPDATE_DEVICE_STATS = Manifest.permission.UPDATE_DEVICE_STATS
        private const val PERMISSION_INTERNET = Manifest.permission.INTERNET
        private val SUPPORTED_BPF_PERMISSIONS =
            listOf(
                PERMISSION_ACCESS_LOCAL_NETWORK,
                PERMISSION_UPDATE_DEVICE_STATS,
                PERMISSION_INTERNET,
            )
    }
}
