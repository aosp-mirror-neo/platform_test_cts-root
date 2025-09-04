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
package android.content.pm.cts_root;

import static android.content.pm.cts_root.utils.Constants.MODE_PASS;
import static android.content.pm.cts_root.utils.Constants.MODE_REJECT;
import static android.content.pm.cts_root.utils.Utils.setDefaultVerificationPolicy;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.cts_root.utils.Constants;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * The goal of this test is to make sure that the verifier works fine on a multi-user device.
 * For this test we use the privileged version of the installer targeting 37. We need the privileged
 * version of the installer because it needs to send broadcasts across users.
 */
public class DeveloperVerificationInstallerMultiUserTest extends DeveloperVerificationTestBase {
    /**
     * For this test we only care about the most restrictive verification policy.
     */
    @Parameterized.Parameters
    public static Iterable<Object> initParameters() {
        return Arrays.asList(
                PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED
        );
    }

    private static final String TEST_ARG_IS_PRIVILEGED_INSTALLER = "is_installer_privileged";
    private static final String TEST_ARG_INSTALLER_PACKAGE_NAME = "installer_package_name";

    private static final boolean sTestArgIsInstallerPrivileged = Boolean.parseBoolean(
            InstrumentationRegistry.getArguments().getString(
                    TEST_ARG_IS_PRIVILEGED_INSTALLER, /* defaultValue= */ "false"));

    private static final String sInstallerPackageName =
            InstrumentationRegistry.getArguments().getString(
                    TEST_ARG_INSTALLER_PACKAGE_NAME, /* defaultValue= */ "invalid_package_name");

    private static Context sContext;
    private UiDevice mUiDevice;
    private int mCurrentUser;
    private int mOtherUser;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Prepare another user
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        sContext = instrumentation.getTargetContext();
        mUiDevice = UiDevice.getInstance(instrumentation);
        mCurrentUser = sContext.getUserId();
        mOtherUser = createUser("Test User");
        assertThat(startUser(mOtherUser, true)).isTrue();
        // Make sure that the installer and the verifier are installed on the other user
        installExistingPackageAsUser(sInstallerPackageName, mOtherUser);
        installExistingPackageAsUser(Constants.VERIFIER_PACKAGE_NAME, mOtherUser);
        assertThat(isPackageInstalledForUser(sInstallerPackageName, mOtherUser)).isTrue();
        assertThat(isPackageInstalledForUser(Constants.VERIFIER_PACKAGE_NAME, mOtherUser))
                .isTrue();
        // Change the default verification policy for the other user
        setDefaultVerificationPolicy(
                sContext, sDefaultPolicy, mCurrentUser, mOtherUser, DEFAULT_TIMEOUT_MS);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        stopUser(mOtherUser, true, true);
        removeUser(mOtherUser);
    }

    @Test
    public void testInstallWithVerifierPassOnOtherUser() throws Exception {
        TestParams testParams = getTestParamsBuilder(mOtherUser)
                .setVerifierMode(MODE_PASS)
                .setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS)
                .build();
        installAndVerifyResults(testParams);
        // Assert that the test app is only installed on the other user
        assertThat(isPackageInstalledForUser(Constants.EMPTY_TEST_PACKAGE_NAME, mOtherUser))
                .isTrue();
        assertThat(isPackageInstalledForUser(Constants.EMPTY_TEST_PACKAGE_NAME, mCurrentUser))
                .isFalse();
    }

    @Test
    public void testInstallWithVerifierRejectOnOtherUser() throws Exception {
        TestParams testParams = getTestParamsBuilder(mOtherUser)
                .setVerifierMode(MODE_REJECT)
                .setVerifierRejectMessage(REJECT_MESSAGE)
                .setStatusCodeExpected(PackageInstaller.STATUS_FAILURE_ABORTED)
                .setVerificationFailureReasonExpected(
                        PackageInstaller.DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED)
                .setErrorMessageExpected("INSTALL_FAILED_VERIFICATION_FAILURE:"
                        + " Verifier rejected the installation with message: "
                        + REJECT_MESSAGE)
                .setHasUserActionIntentExpected(true)
                .build();
        installAndVerifyResults(testParams);
        // Assert that the test app is not installed on the other user
        assertThat(isPackageInstalledForUser(Constants.EMPTY_TEST_PACKAGE_NAME, mOtherUser))
                .isFalse();
        assertThat(isPackageInstalledForUser(Constants.EMPTY_TEST_PACKAGE_NAME, mCurrentUser))
                .isFalse();
    }

    private static TestParams.TestParamsBuilder getTestParamsBuilder(int userId) {
        return new TestParams.TestParamsBuilder()
                .setUserId(userId)
                .setInstallerPackageName(sInstallerPackageName)
                .setIsInstallConfirmationExpected(!sTestArgIsInstallerPrivileged);
    }

    private int createUser(String name) throws Exception {
        // Must use a profile so that the installer activity can be launched
        final String output = mUiDevice.executeShellCommand(
                "pm create-user --profileOf 0 --user-type android.os.usertype.profile.MANAGED "
                        + name);
        if (output.startsWith("Success")) {
            return Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
        }
        throw new IllegalStateException(String.format("Failed to create user: %s", output));
    }

    private boolean startUser(int userId, boolean waitFlag) throws Exception {
        String cmd = "am start-user " + (waitFlag ? "-w " : "") + userId;

        final String output = mUiDevice.executeShellCommand(cmd);
        if (output.startsWith("Error")) {
            return false;
        }
        if (waitFlag) {
            String state = mUiDevice.executeShellCommand("am get-started-user-state " + userId);
            return state.contains("RUNNING_UNLOCKED");
        }
        return true;
    }

    private void stopUser(int userId, boolean waitFlag, boolean forceFlag)
            throws Exception {
        StringBuilder cmd = new StringBuilder("am stop-user ");
        if (waitFlag) {
            cmd.append("-w ");
        }
        if (forceFlag) {
            cmd.append("-f ");
        }
        cmd.append(userId);
        mUiDevice.executeShellCommand(cmd.toString());
    }

    private void installExistingPackageAsUser(String packageName, int userId) throws Exception {
        PackageInstaller pi = sContext.getPackageManager().getPackageInstaller();
        final CompletableFuture<Integer> status = new CompletableFuture<>();
        SystemUtil.runWithShellPermissionIdentity(() -> {
            pi.installExistingPackage(packageName, userId,
                    new IntentSender((IIntentSender) new IIntentSender.Stub() {
                        @Override
                        public void send(int code, Intent intent, String resolvedType,
                                IBinder whitelistToken,
                                IIntentReceiver finishedReceiver,
                                String requiredPermission, Bundle options) {
                            status.complete(intent.getIntExtra(
                                    PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE));
                        }
                    })); },
                Manifest.permission.INSTALL_PACKAGES, Manifest.permission.INTERACT_ACROSS_USERS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        assertThat(status.get()).isEqualTo(PackageInstaller.STATUS_SUCCESS);
    }

    private void removeUser(final int userId) throws Exception {
        mUiDevice.executeShellCommand(String.format("pm remove-user %s", userId));
    }

    private boolean isPackageInstalledForUser(String packageName, int userId) {
        return SystemUtil.runWithShellPermissionIdentity(() -> {
            try {
                sContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0)
                        .getPackageManager().getPackageInfo(packageName, /* flags= */ 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }
}
