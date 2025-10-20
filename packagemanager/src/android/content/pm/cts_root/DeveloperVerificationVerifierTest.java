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

import static android.content.pm.Flags.FLAG_VERIFICATION_SERVICE;
import static android.content.pm.cts_root.utils.Constants.APK_PATH_BASE;
import static android.content.pm.cts_root.utils.Constants.VERIFIER_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that the updated version of the verifier app cannot be uninstalled.
 */
@RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
@RunWith(AndroidJUnit4.class)
public class DeveloperVerificationVerifierTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    private static final String VERIFIER_V3_APK_PATH = APK_PATH_BASE + "CtsRootVerifierAppV3.apk";
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @BeforeClass
    public static void setUpBeforeClass() {
        // Test that the test verifier is the system's default developer verifier
        final ComponentName verifierComponentName =
                sContext.getPackageManager().getPackageInstaller()
                        .getDeveloperVerificationServiceProvider();
        assertThat(verifierComponentName).isNotNull();
        assertThat(verifierComponentName.getPackageName()).isEqualTo(VERIFIER_PACKAGE_NAME);
    }

    @Test
    public void testUpdatedVerifierSupportRollback() throws Exception {
        PackageInfo verifierInfo = sContext.getPackageManager().getPackageInfo(
                VERIFIER_PACKAGE_NAME, /* flags= */ 0);
        // Check that the old version is V2
        assertThat(verifierInfo.getLongVersionCode()).isEqualTo(2);
        // Install the V3 version with rollback enabled
        installApkFromPathWithRollbackEnabled(VERIFIER_V3_APK_PATH);
        verifierInfo = sContext.getPackageManager().getPackageInfo(
                VERIFIER_PACKAGE_NAME, /* flags= */ 0);
        assertThat(verifierInfo.getLongVersionCode()).isEqualTo(3);
        // Now rollback and check that the old version is back
        doRollback(VERIFIER_PACKAGE_NAME);
        verifierInfo = sContext.getPackageManager().getPackageInfo(
                VERIFIER_PACKAGE_NAME, /* flags= */ 0);
        assertThat(verifierInfo.getLongVersionCode()).isEqualTo(2);
    }

    private static void installApkFromPathWithRollbackEnabled(String apkPath) {
        String result =
                SystemUtil.runShellCommandOrThrow(
                        "pm install --enable-rollback " + apkPath).trim();
        assertThat(result).isEqualTo("Success");
    }

    private static void doRollback(String packageName) {
        String result =
                SystemUtil.runShellCommandOrThrow("pm rollback-app " + packageName).trim();
        // This might take tens of seconds to complete
        assertThat(result).isEqualTo("Success");
    }
}
