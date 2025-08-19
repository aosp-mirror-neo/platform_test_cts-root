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

import static android.content.pm.cts_root.utils.Constants.MODE_CRASH;
import static android.content.pm.cts_root.utils.Constants.MODE_INCOMPLETE_NETWORK_UNAVAILABLE;
import static android.content.pm.cts_root.utils.Constants.MODE_INCOMPLETE_UNKNOWN;
import static android.content.pm.cts_root.utils.Constants.MODE_REJECT;
import static android.content.pm.cts_root.utils.Constants.MODE_REJECT_WITH_EXTENSION_RESPONSE;
import static android.content.pm.cts_root.utils.Constants.MODE_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Non privileged installer targeting 35 and below will request user confirmation most of the time
 * including blocking failures.
 */
public class DeveloperVerificationInstallerTarget35Test
        extends DeveloperVerificationInstallerTestBase {

    static {
        try {
            final ApplicationInfo installerInfo = sContext.getPackageManager().getPackageInfo(
                    sInstallerPackageName, /* flags= */ 0).applicationInfo;
            // Check that the target sdk of the installer matches with the expectation
            assertThat(installerInfo).isNotNull();
            assertThat(installerInfo.targetSdkVersion).isEqualTo(
                    Build.VERSION_CODES.VANILLA_ICE_CREAM);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected TestParams getInstallWhenVerifierCrashTestParams() {
        TestParams.TestParamsBuilder testParamsBuilder = getTestParamsBuilder()
                .setVerifierMode(MODE_CRASH);
        if (sDefaultPolicy == PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE
                || sDefaultPolicy
                == PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN) {
            testParamsBuilder.setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS);
        } else {
            testParamsBuilder.setStatusCodeExpected(PackageInstaller.STATUS_PENDING_USER_ACTION)
                    .setHasUserActionIntentExpected(true);
        }
        return testParamsBuilder.build();
    }

    @Override
    protected TestParams getInstallWhenVerifierTimeoutTestParams() {
        TestParams.TestParamsBuilder testParamsBuilder = getTestParamsBuilder()
                .setVerifierMode(MODE_TIMEOUT);
        if (sDefaultPolicy == PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE
                || sDefaultPolicy
                == PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN) {
            testParamsBuilder.setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS);
        } else {
            testParamsBuilder.setStatusCodeExpected(PackageInstaller.STATUS_PENDING_USER_ACTION)
                    .setHasUserActionIntentExpected(true);
        }
        return testParamsBuilder.build();
    }

    @Override
    protected TestParams getInstallWhenVerifierIncompleteUnknownTestParams() {
        TestParams.TestParamsBuilder testParamsBuilder = getTestParamsBuilder()
                .setVerifierMode(MODE_INCOMPLETE_UNKNOWN);
        if (sDefaultPolicy == PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE) {
            testParamsBuilder.setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS);
        } else {
            testParamsBuilder
                    .setStatusCodeExpected(PackageInstaller.STATUS_PENDING_USER_ACTION)
                    .setHasUserActionIntentExpected(true);
        }
        return testParamsBuilder.build();
    }

    @Override
    protected TestParams getInstallWithVerifierIncompleteNetworkUnavailableTestParams() {
        TestParams.TestParamsBuilder testParamsBuilder = getTestParamsBuilder()
                .setVerifierMode(MODE_INCOMPLETE_NETWORK_UNAVAILABLE);
        if (sDefaultPolicy == PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE) {
            testParamsBuilder.setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS);
        } else {
            testParamsBuilder
                    .setStatusCodeExpected(PackageInstaller.STATUS_PENDING_USER_ACTION)
                    .setHasUserActionIntentExpected(true);
        }
        return testParamsBuilder.build();
    }

    @Override
    protected TestParams getInstallWithVerifierRejectTestParams() {
        TestParams.TestParamsBuilder testParamsBuilder = getTestParamsBuilder()
                .setVerifierMode(MODE_REJECT);
        if (sDefaultPolicy == PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE) {
            testParamsBuilder.setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS);
        } else {
            testParamsBuilder
                    .setStatusCodeExpected(PackageInstaller.STATUS_PENDING_USER_ACTION)
                    .setHasUserActionIntentExpected(true);
        }
        return testParamsBuilder.build();
    }

    @Override
    protected TestParams getInstallWithVerifierRejectWithExtensionResponseTestParams() {
        TestParams.TestParamsBuilder testParamsBuilder = getTestParamsBuilder()
                .setVerifierMode(MODE_REJECT_WITH_EXTENSION_RESPONSE);
        if (sDefaultPolicy == PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE) {
            testParamsBuilder.setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS);
        } else {
            testParamsBuilder
                    .setStatusCodeExpected(PackageInstaller.STATUS_PENDING_USER_ACTION)
                    .setHasUserActionIntentExpected(true);
        }
        return testParamsBuilder.build();
    }
}
