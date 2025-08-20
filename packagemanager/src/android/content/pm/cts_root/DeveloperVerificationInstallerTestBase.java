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

import static android.content.pm.cts_root.utils.Constants.INSTALLER_PACKAGE_NAME;
import static android.content.pm.cts_root.utils.Constants.MODE_BYPASS;
import static android.content.pm.cts_root.utils.Constants.MODE_LITE;
import static android.content.pm.cts_root.utils.Constants.MODE_PASS;
import static android.content.pm.cts_root.utils.Constants.MODE_REJECT_WITH_POLICY_OVERRIDE;
import static android.content.pm.cts_root.utils.Constants.PRIV_INSTALLER_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

public abstract class DeveloperVerificationInstallerTestBase extends DeveloperVerificationTestBase {

    private static final String TEST_ARG_IS_PRIVILEGED_INSTALLER = "is_installer_privileged";
    private static final String TEST_ARG_INSTALLER_PACKAGE_NAME = "installer_package_name";

    private static final boolean sTestArgIsInstallerPrivileged = Boolean.parseBoolean(
            InstrumentationRegistry.getArguments().getString(
                    TEST_ARG_IS_PRIVILEGED_INSTALLER, /* defaultValue= */ "false"));

    protected static final String sInstallerPackageName =
            InstrumentationRegistry.getArguments().getString(
                    TEST_ARG_INSTALLER_PACKAGE_NAME, /* defaultValue= */ "invalid_package_name");

    static {
        try {
            final ApplicationInfo installerInfo = sContext.getPackageManager().getPackageInfo(
                    sInstallerPackageName, /* flags= */ 0).applicationInfo;
            // Check that the status of the installer matches with the expectation
            assertThat(installerInfo).isNotNull();
            assertThat(installerInfo.isPrivilegedApp()).isEqualTo(sTestArgIsInstallerPrivileged);
            if (installerInfo.isPrivilegedApp()) {
                assertThat(installerInfo.packageName).isEqualTo(PRIV_INSTALLER_PACKAGE_NAME);
            } else {
                assertThat(installerInfo.packageName).isEqualTo(INSTALLER_PACKAGE_NAME);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static TestParams.TestParamsBuilder getTestParamsBuilder() {
        return new TestParams.TestParamsBuilder()
                .setInstallerPackageName(sInstallerPackageName)
                .setIsInstallConfirmationExpected(!sTestArgIsInstallerPrivileged);
    }

    protected abstract TestParams getInstallWhenVerifierCrashTestParams();
    protected abstract TestParams getInstallWhenVerifierTimeoutTestParams();
    protected abstract TestParams getInstallWhenVerifierIncompleteUnknownTestParams();
    protected abstract TestParams getInstallWithVerifierIncompleteNetworkUnavailableTestParams();
    protected abstract TestParams getInstallWithVerifierRejectTestParams();
    protected abstract TestParams getInstallWithVerifierRejectWithExtensionResponseTestParams();


    // When the verifier returns PASS, regardless of the default policy and the target sdk version
    // of the installer, the installation should always go through.
    @Test
    public void testInstallWithVerifierPass() throws Exception {
        TestParams testParams = getTestParamsBuilder()
                .setVerifierMode(MODE_PASS)
                .setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS)
                .build();
        installAndVerifyResults(testParams);
    }

    // When the verifier returns PASS with LITE=true, regardless of the default policy and the
    // target sdk version of the installer, the installation should always go through.
    @Test
    public void testInstallWithVerifierPassLite() throws Exception {
        TestParams testParams = getTestParamsBuilder()
                .setVerifierMode(MODE_LITE)
                .setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS)
                .build();
        installAndVerifyResults(testParams);
    }

    // When the verifier returns BYPASS, regardless of the default policy and the target sdk version
    // of the installer, the installation should always go through.
    @Test
    public void testInstallWithVerifierBypass() throws Exception {
        TestParams testParams = getTestParamsBuilder()
                .setVerifierMode(MODE_BYPASS)
                .setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS)
                .build();
        installAndVerifyResults(testParams);
    }

    @Test
    public void testInstallWhenVerifierCrash() throws Exception {
        TestParams testParams = getInstallWhenVerifierCrashTestParams();
        installAndVerifyResults(testParams);
    }

    @Test
    public void testInstallWhenVerifierTimeout() throws Exception {
        TestParams testParams = getInstallWhenVerifierTimeoutTestParams();
        installAndVerifyResults(testParams);
    }


    @Test
    public void testInstallWithVerifierIncompleteUnknown() throws Exception {
        TestParams testParams = getInstallWhenVerifierIncompleteUnknownTestParams();
        installAndVerifyResults(testParams);
    }

    @Test
    public void testInstallWithVerifierIncompleteNetworkUnavailable()
            throws Exception {
        TestParams testParams = getInstallWithVerifierIncompleteNetworkUnavailableTestParams();
        installAndVerifyResults(testParams);
    }

    @Test
    public void testInstallWithVerifierReject() throws Exception {
        TestParams testParams = getInstallWithVerifierRejectTestParams();
        installAndVerifyResults(testParams);
    }

    @Test
    public void testInstallWithVerifierRejectWithExtensionResponse() throws Exception {
        TestParams testParams = getInstallWithVerifierRejectWithExtensionResponseTestParams();
        installAndVerifyResults(testParams);
    }

    // With policy overridden to OPEN, the installation should always pass, regardless of the
    // default policy or the target sdk version of the installer.
    @Test
    public void testInstallWithPolicyOverride() throws Exception {
        // Even if the default policy is most restrictive, after overriding session policy to open,
        // the test should still pass even if the verifier returned reject
        TestParams testParams = getTestParamsBuilder()
                .setSessionVerificationPolicyOverride(
                        PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE)
                .setVerifierMode(MODE_REJECT_WITH_POLICY_OVERRIDE)
                .setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS)
                .build();
        installAndVerifyResults(testParams);
    }
}
