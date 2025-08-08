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

import static android.content.pm.cts_root.utils.Constants.APK_PATH_BASE;
import static android.content.pm.cts_root.utils.Constants.EMERGENCY_INSTALLER_PACKAGE_NAME;
import static android.content.pm.cts_root.utils.Constants.MODE_REJECT;
import static android.content.pm.cts_root.utils.Constants.PRIV_INSTALLER_PACKAGE_NAME;
import static android.content.pm.cts_root.utils.Constants.VERIFIER_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInstaller;

import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.Arrays;

public class DeveloperVerificationEmergencyBypassTest extends DeveloperVerificationTestBase {
    private static final String VERIFIER_APK_PATH = APK_PATH_BASE + "CtsRootVerifierApp.apk";
    private static final String PRIV_INSTALLER_APK_PATH =
            APK_PATH_BASE + "CtsRootPrivInstallerAppTarget36.apk";
    private static final String EMERGENCY_INSTALLER_APK_PATH =
            APK_PATH_BASE + "CtsRootEmergencyInstallerApp.apk";

    private static final String PRIV_INSTALLER_SPLIT_APK =
            APK_PATH_BASE + "CtsRootPrivInstallerAppTarget36_mdpi-v4.apk";


    /**
     * For this test we only care about the most restrictive verification policy.
     */
    @Parameterized.Parameters
    public static Iterable<Object> initParameters() {
        return Arrays.asList(
                PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED
        );
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // First check update ownership of the verifier
        InstallSourceInfo sourceInfo = sContext.getPackageManager().getInstallSourceInfo(
                VERIFIER_PACKAGE_NAME);
        assertThat(sourceInfo).isNotNull();
        assertThat(sourceInfo.getUpdateOwnerPackageName()).isEqualTo(
                PRIV_INSTALLER_PACKAGE_NAME);
    }

    /**
     * TODO(b/399436145) there is currently no way to clean up the updated verifier because we don't
     * allow the uninstallation as it is a protected package. After the system version is removed,
     * the updated version will remain as a non-privileged app. Fix it with a proper clean up.
     * For now we must manually force uninstalling the verifier app before running the test.
     */
    @Test
    public void testInstallSucceedsWithRejectUpdatingVerifierItself() throws Exception {
        // Reinstall the verifier with its update owner
        TestParams testParams = new TestParams.TestParamsBuilder()
                .setInstallerPackageName(PRIV_INSTALLER_PACKAGE_NAME)
                .setVerifierMode(MODE_REJECT)
                .setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS)
                .setApkPath(VERIFIER_APK_PATH)
                .setPackageName(VERIFIER_PACKAGE_NAME)
                .build();
        installAndVerifyResults(testParams);
    }

    @Test
    public void testInstallSucceedsWithRejectUpdatingUpdateOwnerOfVerifier()
            throws Exception {
        // Update the update-owner with the update-owner itself. This means that the installer is
        // being updated, but we still like to get the installation result back from it via the
        // broadcast and don't want it to be killed. So here we're only updating it by adding a
        // split and passing the DONT_KILL flag to it to keep it alive.
        TestParams testParams = new TestParams.TestParamsBuilder()
                .setInstallerPackageName(PRIV_INSTALLER_PACKAGE_NAME)
                .setVerifierMode(MODE_REJECT)
                .setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS)
                .setApkPath(PRIV_INSTALLER_SPLIT_APK)
                .setSplitInstall(true)
                .setPackageName(PRIV_INSTALLER_PACKAGE_NAME)
                .build();
        installAndVerifyResults(testParams);
    }

    @Test
    public void testInstallSucceedsWithRejectUpdatingUpdateOwnerOfVerifierWithEmergencyInstaller()
            throws Exception {
        // Now re-install the update-owner with the emergency installer
        TestParams testParams = new TestParams.TestParamsBuilder()
                .setInstallerPackageName(EMERGENCY_INSTALLER_PACKAGE_NAME)
                .setVerifierMode(MODE_REJECT)
                .setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS)
                .setApkPath(PRIV_INSTALLER_APK_PATH)
                .setPackageName(PRIV_INSTALLER_PACKAGE_NAME)
                .build();
        installAndVerifyResults(testParams);
    }

    @Test
    public void testInstallSucceedsWithRejectUpdatingEmergencyInstallerOfUpdateOwnerOfVerifier()
            throws Exception {
        // There is no API to check if the emergency installer of the update owner package is set,
        // So we will just directly try to update the emergency installer and check that the install
        // is successful.
        TestParams testParams = new TestParams.TestParamsBuilder()
                .setInstallerPackageName(PRIV_INSTALLER_PACKAGE_NAME)
                .setVerifierMode(MODE_REJECT)
                .setStatusCodeExpected(PackageInstaller.STATUS_SUCCESS)
                .setApkPath(EMERGENCY_INSTALLER_APK_PATH)
                .setPackageName(EMERGENCY_INSTALLER_PACKAGE_NAME)
                .build();
        installAndVerifyResults(testParams);
    }
}
