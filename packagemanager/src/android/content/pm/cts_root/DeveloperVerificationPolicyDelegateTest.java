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
import static android.content.pm.cts_root.utils.Constants.ACTION_QUERY_DELEGATE;
import static android.content.pm.cts_root.utils.Constants.ACTION_REPORT_QUERY_DELEGATE_RESULT;
import static android.content.pm.cts_root.utils.Constants.DELEGATE_PACKAGE_NAME;
import static android.content.pm.cts_root.utils.Constants.EXTRA_DELEGATE_PACKAGE_NAME;
import static android.content.pm.cts_root.utils.Constants.VERIFIER_PACKAGE_NAME;
import static android.content.pm.cts_root.utils.Utils.getDefaultVerificationPolicy;
import static android.content.pm.cts_root.utils.Utils.isLowRamDevice;
import static android.content.pm.cts_root.utils.Utils.setDefaultVerificationPolicy;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
@RunWith(AndroidJUnit4.class)
public class DeveloperVerificationPolicyDelegateTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final long DEFAULT_TIMEOUT_MS = isLowRamDevice(sContext)
            ? TimeUnit.SECONDS.toMillis(60)
            : TimeUnit.SECONDS.toMillis(15);

    @BeforeClass
    public static void setUpBeforeClass() throws PackageManager.NameNotFoundException {
        // Test that the test verifier is the system's default developer verifier
        final ComponentName verifierComponentName =
                sContext.getPackageManager().getPackageInstaller()
                        .getDeveloperVerificationServiceProvider();
        assertThat(verifierComponentName).isNotNull();
        assertThat(verifierComponentName.getPackageName()).isEqualTo(VERIFIER_PACKAGE_NAME);
        // Test that the delegate app exists
        assertThat(sContext.getPackageManager().getPackageInfo(DELEGATE_PACKAGE_NAME, 0))
                .isNotNull();
    }

    @Test
    public void testVerifierCanQueryDelegate() throws Exception {
        final CompletableFuture<Intent> queryDelegateResult = new CompletableFuture<>();
        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_REPORT_QUERY_DELEGATE_RESULT.equals(intent.getAction())) {
                    queryDelegateResult.complete(intent);
                }
            }
        };
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_REPORT_QUERY_DELEGATE_RESULT);
        try {
            sContext.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);

            Intent intent = new Intent(ACTION_QUERY_DELEGATE);
            intent.setPackage(VERIFIER_PACKAGE_NAME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            sContext.startActivity(intent);
            // Wait to confirm that the delegate app is set by the system and can be queried by
            // the verifier app.
            assertThat(queryDelegateResult.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .getStringExtra(EXTRA_DELEGATE_PACKAGE_NAME)).isEqualTo(DELEGATE_PACKAGE_NAME);
        } finally {
            sContext.unregisterReceiver(broadcastReceiver);
        }
    }

    @Test
    public void testDelegateCanSetAndGetPolicy() throws Exception {
        final int userId = sContext.getUserId();
        // Remember the old policy value
        final int oldPolicy = getDefaultVerificationPolicy(sContext);
        final int newPolicy = getNextPolicyForTest(oldPolicy);
        setDefaultVerificationPolicy(sContext, newPolicy, userId, userId, DEFAULT_TIMEOUT_MS);
        assertThat(getDefaultVerificationPolicy(sContext)).isEqualTo(newPolicy);
        // Restore the old policy value
        setDefaultVerificationPolicy(sContext, oldPolicy, userId, userId, DEFAULT_TIMEOUT_MS);
        assertThat(getDefaultVerificationPolicy(sContext)).isEqualTo(oldPolicy);
    }

    private static int getNextPolicyForTest(int currentPolicy) {
        if (currentPolicy == PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED) {
            return PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE;
        } else {
            return currentPolicy + 1;
        }
    }
}
