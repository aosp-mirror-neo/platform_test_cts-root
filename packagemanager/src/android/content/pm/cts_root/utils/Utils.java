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

import static android.content.pm.cts_root.utils.Constants.ACTION_INSTALLER_RESULT;
import static android.content.pm.cts_root.utils.Constants.ACTION_MODE_SET;
import static android.content.pm.cts_root.utils.Constants.ACTION_REPORT_POLICY_SET_RESULT;
import static android.content.pm.cts_root.utils.Constants.ACTION_SET_POLICY;
import static android.content.pm.cts_root.utils.Constants.ACTION_SET_TEST_MODE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_EXTENSION_RESPONSE_KEY;
import static android.content.pm.cts_root.utils.Constants.EXTRA_EXTENSION_RESPONSE_VALUE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_INSTALL_APK_PATH;
import static android.content.pm.cts_root.utils.Constants.EXTRA_INSTALL_PACKAGE_NAME;
import static android.content.pm.cts_root.utils.Constants.EXTRA_INSTALL_RESULT_INTENT;
import static android.content.pm.cts_root.utils.Constants.EXTRA_IS_SPLIT_INSTALL;
import static android.content.pm.cts_root.utils.Constants.EXTRA_POLICY_VALUE;
import static android.content.pm.cts_root.utils.Constants.DELEGATE_PACKAGE_NAME;
import static android.content.pm.cts_root.utils.Constants.EXTRA_REJECT_MESSAGE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_SESSION_VERIFICATION_POLICY_OVERRIDE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_TEST_MODE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_TEST_USER;
import static android.content.pm.cts_root.utils.Constants.MODE_PASS;
import static android.content.pm.cts_root.utils.Constants.VERIFIER_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
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
            int targetUserId)
            throws Exception {
        requestDelegateAppToSetPolicy(context, policy, sourceUserId, targetUserId,
                getDefaultTimeoutMillis(context));
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
        final IntentFilter intentFilter = new IntentFilter(ACTION_REPORT_POLICY_SET_RESULT);
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

    /**
     * Sets the verifier mode.
     */
    public static void configureVerifier(Context context, @Constants.Mode int mode,
            @Nullable String rejectMessage,
            @Nullable String extensionResponseValue, int sessionPolicyOverride, int userId)
            throws Exception {
        Intent intent = new Intent(ACTION_SET_TEST_MODE);
        intent.setPackage(VERIFIER_PACKAGE_NAME);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_TEST_MODE, mode);
        final CompletableFuture<Integer> verifierModeSet = new CompletableFuture<>();
        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_MODE_SET.equals(intent.getAction())) {
                    verifierModeSet.complete(intent.getIntExtra(EXTRA_TEST_MODE,
                            /* defaultValue= */ MODE_PASS));
                }
            }
        };
        final IntentFilter intentFilter = new IntentFilter(ACTION_MODE_SET);
        if (rejectMessage != null) {
            intent.putExtra(EXTRA_REJECT_MESSAGE, rejectMessage);
        }
        if (extensionResponseValue != null) {
            intent.putExtra(EXTRA_EXTENSION_RESPONSE_VALUE, extensionResponseValue);
        }
        intent.putExtra(EXTRA_SESSION_VERIFICATION_POLICY_OVERRIDE, sessionPolicyOverride);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        // Attach the user id of the test itself so that the verifier can send back the broadcast
        intent.putExtra(EXTRA_TEST_USER, UserHandle.of(context.getUserId()));
        try {
            context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
            SystemUtil.runWithShellPermissionIdentity(() -> {
                context.startActivityAsUser(intent, UserHandle.of(userId));
            }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
            // Wait to confirm that the mode is set by the verifier app
            assertThat(verifierModeSet.get(getDefaultTimeoutMillis(context), TimeUnit.MILLISECONDS))
                    .isEqualTo(mode);
        } finally {
            context.unregisterReceiver(broadcastReceiver);
        }
    }

    /**
     * Returns the default timeout for waiting.
     */
    public static long getDefaultTimeoutMillis(Context context) {
        return isLowRamDevice(context)
                ? TimeUnit.SECONDS.toMillis(60) : TimeUnit.SECONDS.toMillis(15);
    }

    /**
     * Configures and launches the installer and waits for the result.
     */
    public static Intent configureAndLaunchInstaller(
            Context context, String installerPackageName,
            @Nullable String extensionResponseKey, @NonNull String apkPath,
            @NonNull String packageName, boolean isSplitInstall,
            boolean isInstallConfirmationExpected, int userId) throws Exception {
        final long defaultTimeoutMillis = getDefaultTimeoutMillis(context);
        CompletableFuture<Intent> installerResult = new CompletableFuture<>();
        CompletableFuture<Boolean> installConfirmationReceived = new CompletableFuture<>();
        final InstallerResultBroadcastReceiver
                mBroadcastReceiver = new InstallerResultBroadcastReceiver(
                installerResult, installConfirmationReceived);
        final IntentFilter intentFilter = new IntentFilter(ACTION_INSTALLER_RESULT);
        try {
            context.registerReceiver(mBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
            // Start the installer app to install a test app at the given path and listen for result
            SystemUtil.runWithShellPermissionIdentity(() -> {
                context.startActivityAsUser(
                        createLaunchInstallerIntent(context, installerPackageName,
                                extensionResponseKey, apkPath, packageName, isSplitInstall),
                        UserHandle.of(userId));
            }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
            // For a non-privileged installer, verify that the first user action requested was for
            // the install confirmation.
            if (isInstallConfirmationExpected) {
                assertThat(installConfirmationReceived.get(
                        defaultTimeoutMillis, TimeUnit.MILLISECONDS)).isTrue();
            }
            return installerResult.get(defaultTimeoutMillis, TimeUnit.MILLISECONDS);
        } finally {
            context.unregisterReceiver(mBroadcastReceiver);
        }
    }

    private static Intent createLaunchInstallerIntent(Context context, String installerPackageName,
            @Nullable String extensionResponseKey, @NonNull String apkPath,
            @NonNull String packageName, boolean isSplitInstall) {
        Intent intent = new Intent("cts_root.action.LAUNCH_INSTALLER");
        intent.setPackage(installerPackageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        if (extensionResponseKey != null) {
            intent.putExtra(EXTRA_EXTENSION_RESPONSE_KEY, extensionResponseKey);
        }
        intent.putExtra(EXTRA_INSTALL_APK_PATH, apkPath);
        intent.putExtra(EXTRA_INSTALL_PACKAGE_NAME, packageName);
        intent.putExtra(EXTRA_IS_SPLIT_INSTALL, isSplitInstall);
        // Attach the user id of the test itself so that the installer can send back the broadcast
        intent.putExtra(EXTRA_TEST_USER, UserHandle.of(context.getUserId()));
        return intent;
    }

    private static class InstallerResultBroadcastReceiver extends BroadcastReceiver {
        private final CompletableFuture<Intent> mInstallerResult;
        private final CompletableFuture<Boolean> mInstallConfirmationReceived;
        InstallerResultBroadcastReceiver(CompletableFuture<Intent> installerResult,
                CompletableFuture<Boolean> installConfirmationReceived) {
            mInstallerResult = installerResult;
            mInstallConfirmationReceived = installConfirmationReceived;
        }
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case ACTION_INSTALLER_RESULT:
                    final Intent resultIntent = intent.getParcelableExtra(
                            EXTRA_INSTALL_RESULT_INTENT, Intent.class);
                    if (resultIntent == null) {
                        break;
                    }
                    final Intent extraIntent = resultIntent.getParcelableExtra(
                            Intent.EXTRA_INTENT, Intent.class);
                    final int sessionId = resultIntent.getIntExtra(
                            PackageInstaller.EXTRA_SESSION_ID,
                            PackageInstaller.SessionInfo.INVALID_ID);
                    // For a non-privileged installer, a user confirmation dialog would first
                    // appear. We are not interested in that dialog, so just bypass it directly by
                    // reporting to the system that the user has confirmed the installation, so that
                    // the installation can proceed to the verification stage.
                    if (extraIntent != null
                            && PackageInstaller.ACTION_CONFIRM_INSTALL.equals(
                            extraIntent.getAction())
                            && sessionId != PackageInstaller.SessionInfo.INVALID_ID) {
                        final PackageInstaller packageInstaller =
                                context.getPackageManager().getPackageInstaller();
                        SystemUtil.runWithShellPermissionIdentity(
                                () -> packageInstaller.setPermissionsResult(
                                        sessionId, /* accepted= */ true),
                                Manifest.permission.INSTALL_PACKAGES);
                        mInstallConfirmationReceived.complete(true);
                    } else {
                        // Otherwise, directly go to check the content of the result intent.
                        mInstallerResult.complete(resultIntent);
                    }
                    break;
                case null:
                default:
                    break;
            }
        }
    }
}
