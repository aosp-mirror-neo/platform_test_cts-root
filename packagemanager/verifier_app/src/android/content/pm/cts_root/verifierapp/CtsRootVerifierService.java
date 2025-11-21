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

package android.content.pm.cts_root.verifierapp;

import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE;
import static android.content.pm.cts_root.utils.Constants.MODE_BYPASS;
import static android.content.pm.cts_root.utils.Constants.MODE_CRASH;
import static android.content.pm.cts_root.utils.Constants.MODE_INCOMPLETE_NETWORK_UNAVAILABLE;
import static android.content.pm.cts_root.utils.Constants.MODE_INCOMPLETE_UNKNOWN;
import static android.content.pm.cts_root.utils.Constants.MODE_LITE;
import static android.content.pm.cts_root.utils.Constants.MODE_PASS;
import static android.content.pm.cts_root.utils.Constants.MODE_PASS_WITH_VERIFICATION_FLAGS_RESPONSE;
import static android.content.pm.cts_root.utils.Constants.MODE_REJECT;
import static android.content.pm.cts_root.utils.Constants.MODE_REJECT_WITH_EXTENSION_RESPONSE;
import static android.content.pm.cts_root.utils.Constants.MODE_TIMEOUT;
import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_NETWORK_UNAVAILABLE;
import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN;

import android.content.Intent;
import android.content.pm.verify.developer.DeveloperVerificationSession;
import android.content.pm.verify.developer.DeveloperVerificationStatus;
import android.content.pm.verify.developer.DeveloperVerifierService;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class CtsRootVerifierService extends DeveloperVerifierService {
    private static final String TAG = CtsRootVerifierService.class.getSimpleName();
    static final String PREFERENCE_KEY_MODE = "mode";
    static final String PREFERENCE_KEY_POLICY_OVERRIDE = "policy-override";
    static final String PREFERENCE_KEY_POLICY_OVERRIDE_ENABLED = "policy-override-enabled";
    static final String PREFERENCE_KEY_REJECT_MESSAGE = "reject_message";
    static final String PREFERENCE_EXTENSION_RESPONSE_VALUE = "extension_response_value";

    private final int mUserId = UserHandle.myUserId();

    @Override
    public void onCreate() {
        Log.i(TAG, "service is started on user " + mUserId);
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "service is bound on user " + mUserId);
        return super.onBind(intent);
    }

    @Override
    public void onPackageNameAvailable(String packageName) {
        Log.i(TAG, "onPackageNameAvailable on user " + mUserId + ": " + packageName);
    }

    @Override
    public void onVerificationCancelled(String packageName) {
        Log.i(TAG, "onVerificationCancelled on user " + mUserId + ": " + packageName);
    }

    @Override
    public void onVerificationRequired(DeveloperVerificationSession session) {
        Log.i(TAG, "onVerificationRequired on user " + mUserId + ": " + session.getId());
        final int testMode = getTestMode();
        DeveloperVerificationStatus status;

        // First override the session policy if the override has been requested
        if (isPolicyOverrideEnabled()) {
            int policyOverride = getPolicyOverride();
            session.setPolicy(policyOverride);
        }
        final PersistableBundle extensionResponse = session.getExtensionParams();
        final String extensionResponseValue = getExtensionResponseValue();
        switch (testMode) {
            case MODE_CRASH:
                // Stop this service to simulate a crash.
                // TODO(b/399436145): currently doesn't trigger a disconnection somehow.
                android.os.Process.killProcess(android.os.Process.myPid());
                break;
            case MODE_INCOMPLETE_NETWORK_UNAVAILABLE:
                session.reportVerificationIncomplete(
                        DEVELOPER_VERIFICATION_INCOMPLETE_NETWORK_UNAVAILABLE);
                Log.i(TAG, "Reported verification incomplete network unavailable.");
                break;
            case MODE_INCOMPLETE_UNKNOWN:
                session.reportVerificationIncomplete(DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN);
                Log.i(TAG, "Reported verification incomplete unknown.");
                break;
            case MODE_REJECT:
                // Immediately return verification failure to reject the installation
                status =
                        new DeveloperVerificationStatus.Builder()
                                .setVerified(false)
                                .setFailureMessage(getRejectMessage())
                                .build();
                session.reportVerificationComplete(status);
                Log.i(TAG, "Reported verification reject.");
                break;
            case MODE_REJECT_WITH_EXTENSION_RESPONSE:
                // Immediately return verification failure to reject the installation
                status =
                        new DeveloperVerificationStatus.Builder()
                                .setVerified(false)
                                .setFailureMessage(getRejectMessage())
                                .build();
                if (!extensionResponseValue.isEmpty()) {
                    // For every key in the extensionParam, set its value as expected
                    for (String key : extensionResponse.keySet()) {
                        extensionResponse.putString(key, extensionResponseValue);
                    }
                }
                session.reportVerificationComplete(status, extensionResponse);
                Log.i(TAG, "Reported verification reject with extension bundle.");
                break;
            case MODE_TIMEOUT:
                // Do not return anything back to the system
                Log.i(TAG, "Not reporting anything to act like a timeout.");
                break;
            case MODE_LITE:
                status = new DeveloperVerificationStatus.Builder()
                        .setVerified(true)
                        .setLiteVerification(true)
                        .build();
                session.reportVerificationComplete(status);
                Log.i(TAG, "Reported verification pass with lite verification.");
                break;
            case MODE_BYPASS:
                session.reportVerificationBypassed(/* bypassReason= */ 10);
                Log.i(TAG, "Reported verification bypassed.");
                break;
            case MODE_PASS_WITH_VERIFICATION_FLAGS_RESPONSE:
                status = new DeveloperVerificationStatus.Builder().setVerified(true).build();
                int verificationFlags = session.getVerificationFlags();
                extensionResponse.putString(
                        "verification-flags", String.valueOf(verificationFlags));
                session.reportVerificationComplete(status, extensionResponse);
                Log.i(
                        TAG, "Reported verification pass with session flags: " + verificationFlags);
                break;
            case MODE_PASS:
                // passthrough
            default:
                // Immediately return success
                status = new DeveloperVerificationStatus.Builder().setVerified(true).build();
                if (!extensionResponseValue.isEmpty()) {
                    // For every key in the extensionParam, set its value as expected
                    for (String key : extensionResponse.keySet()) {
                        extensionResponse.putString(key, extensionResponseValue);
                    }
                }
                session.reportVerificationComplete(status, extensionResponse);
                Log.i(TAG, "Reported verification pass.");
                break;
        }
    }

    @Override
    public void onVerificationRetry(DeveloperVerificationSession session) {
        Log.i(TAG, "onVerificationRetry on user " + mUserId + ": " + session.getPackageName());
        // Always allow retry to pass
        DeveloperVerificationStatus status = new DeveloperVerificationStatus.Builder()
                .setVerified(true).build();
        session.reportVerificationComplete(status);
        Log.i(TAG, "Reported verification pass on retry.");
    }

    @Override
    public void onVerificationTimeout(int verificationId) {
        Log.i(TAG, "onVerificationTimeout on user " + mUserId + ": " + verificationId);
    }

    private int getTestMode() {
        return PreferenceManager.getDefaultSharedPreferences(this).getInt(
                PREFERENCE_KEY_MODE, /* defValue= */ MODE_PASS);
    }

    private boolean isPolicyOverrideEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREFERENCE_KEY_POLICY_OVERRIDE_ENABLED, false);
    }

    private int getPolicyOverride() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getInt(PREFERENCE_KEY_POLICY_OVERRIDE, DEVELOPER_VERIFICATION_POLICY_NONE);
    }

    private String getRejectMessage() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getString(PREFERENCE_KEY_REJECT_MESSAGE, "");
    }

    private String getExtensionResponseValue() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getString(PREFERENCE_EXTENSION_RESPONSE_VALUE, "");
    }
}
