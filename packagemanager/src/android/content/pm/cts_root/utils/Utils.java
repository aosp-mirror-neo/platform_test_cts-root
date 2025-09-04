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

package android.content.pm.cts_root.utils;

import static android.content.pm.cts_root.utils.Constants.ACTION_REPORT_POLICY_SET_RESULT;
import static android.content.pm.cts_root.utils.Constants.ACTION_SET_POLICY;
import static android.content.pm.cts_root.utils.Constants.EXTRA_POLICY_VALUE;
import static android.content.pm.cts_root.utils.Constants.DELEGATE_PACKAGE_NAME;
import static android.content.pm.cts_root.utils.Constants.EXTRA_TEST_USER;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.UserHandle;

import com.android.compatibility.common.util.SystemUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Utils {
    /**
     * Returns true if the device is a low RAM device.
     */
    public static boolean isLowRamDevice(Context context) {
        return context.getSystemService(ActivityManager.class).isLowRamDevice();
    }

    /**
     * Returns the default verification policy on the device.
     */
    public static int getDefaultVerificationPolicy(Context context) {
        return SystemUtil.runWithShellPermissionIdentity(
                () -> context.getPackageManager().getPackageInstaller()
                        .getDeveloperVerificationPolicy());
    }

    /**
     * Sets the default verification policy on the device via the policy delegate app.
     */
    public static void setDefaultVerificationPolicy(Context context,
            @PackageInstaller.DeveloperVerificationPolicy int policy, int sourceUserId,
            int targetUserId, long timeoutMs)
            throws Exception {
        requestDelegateAppToSetPolicy(context, policy, sourceUserId, targetUserId, timeoutMs);
        assertThat(getDefaultVerificationPolicy(context)).isEqualTo(policy);
    }

    private static void requestDelegateAppToSetPolicy(
            Context context, int newPolicy, int sourceUserId, int targetUserId, long timeoutMs)
            throws Exception {
        final CompletableFuture<Intent> setPolicyResult = new CompletableFuture<>();
        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_REPORT_POLICY_SET_RESULT.equals(intent.getAction())) {
                    setPolicyResult.complete(intent);
                }
            }
        };
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_REPORT_POLICY_SET_RESULT);
        try {
            context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
            // Request the delegate app to change the verification policy
            Intent intent = new Intent(ACTION_SET_POLICY);
            intent.setPackage(DELEGATE_PACKAGE_NAME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra(EXTRA_POLICY_VALUE, newPolicy);
            // Attach the user id of the test itself so the delegate app can send back the broadcast
            intent.putExtra(EXTRA_TEST_USER, UserHandle.of(sourceUserId));
            SystemUtil.runWithShellPermissionIdentity(() -> {
                context.startActivityAsUser(intent, UserHandle.of(targetUserId));
            }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
            // Wait to confirm that the delegate app has changed the policy
            assertThat(setPolicyResult.get(timeoutMs, TimeUnit.MILLISECONDS)
                    .getIntExtra(EXTRA_POLICY_VALUE, -100)).isEqualTo(newPolicy);
        } finally {
            context.unregisterReceiver(broadcastReceiver);
        }
    }
}
