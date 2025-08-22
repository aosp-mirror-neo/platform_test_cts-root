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
import static android.content.pm.cts_root.utils.Constants.ACTION_MODE_SET;
import static android.content.pm.cts_root.utils.Constants.ACTION_QUERY_DELEGATE;
import static android.content.pm.cts_root.utils.Constants.ACTION_REPORT_QUERY_DELEGATE_RESULT;
import static android.content.pm.cts_root.utils.Constants.ACTION_SET_TEST_MODE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_DELEGATE_PACKAGE_NAME;
import static android.content.pm.cts_root.utils.Constants.EXTRA_EXTENSION_RESPONSE_VALUE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_REJECT_MESSAGE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_SESSION_VERIFICATION_POLICY_OVERRIDE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_TEST_MODE;
import static android.content.pm.cts_root.utils.Constants.MODE_PASS;
import static android.content.pm.cts_root.utils.Constants.MODE_REJECT;
import static android.content.pm.cts_root.utils.Constants.MODE_REJECT_WITH_EXTENSION_RESPONSE;
import static android.content.pm.cts_root.utils.Constants.MODE_REJECT_WITH_POLICY_OVERRIDE;
import static android.content.pm.cts_root.verifierapp.CtsRootVerifierService.PREFERENCE_EXTENSION_RESPONSE_VALUE;
import static android.content.pm.cts_root.verifierapp.CtsRootVerifierService.PREFERENCE_KEY_MODE;
import static android.content.pm.cts_root.verifierapp.CtsRootVerifierService.PREFERENCE_KEY_POLICY_OVERRIDE;
import static android.content.pm.cts_root.verifierapp.CtsRootVerifierService.PREFERENCE_KEY_POLICY_OVERRIDE_ENABLED;
import static android.content.pm.cts_root.verifierapp.CtsRootVerifierService.PREFERENCE_KEY_REJECT_MESSAGE;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class CtsRootVerifierActivity extends Activity {
    private static final String TAG = CtsRootVerifierActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (ACTION_QUERY_DELEGATE.equals(intent.getAction())) {
            Log.i(TAG, "Query delegate request from automated tests");
            String delegatePackageName = "";
            try {
                delegatePackageName = getApplicationContext().getPackageManager()
                        .getPackageInstaller().getDeveloperVerificationPolicyDelegatePackage();
            } catch (Exception e) {
                Log.e(TAG, "Exception while getting delegate package name", e);
                delegatePackageName = "ERROR";
            }
            Intent broadcastIntent = new Intent(ACTION_REPORT_QUERY_DELEGATE_RESULT);
            broadcastIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            broadcastIntent.putExtra(EXTRA_DELEGATE_PACKAGE_NAME, delegatePackageName);
            sendBroadcast(broadcastIntent);
            finish();
            return;
        }

        if (!ACTION_SET_TEST_MODE.equals(intent.getAction())) {
            return;
        }
        boolean hasMode = intent.hasExtra(EXTRA_TEST_MODE);
        if (!hasMode) {
            return;
        }
        int mode = intent.getIntExtra(EXTRA_TEST_MODE, /* defaultValue= */ MODE_PASS);
        Log.i(TAG, "Mode request from automated tests: " + mode);
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putInt(PREFERENCE_KEY_MODE, mode)
                .apply();
        // Set reject message if specified
        if (mode == MODE_REJECT || mode == MODE_REJECT_WITH_EXTENSION_RESPONSE
                || mode == MODE_REJECT_WITH_POLICY_OVERRIDE) {
            String rejectMessage = intent.getStringExtra(EXTRA_REJECT_MESSAGE);
            Log.i(TAG, "Reject message override requested by automated tests: " + rejectMessage);
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString(PREFERENCE_KEY_REJECT_MESSAGE, rejectMessage)
                    .apply();
        }
        // Set extension response value if specified
        if (intent.hasExtra(EXTRA_EXTENSION_RESPONSE_VALUE)) {
            String extensionResponseValue = intent.getStringExtra(EXTRA_EXTENSION_RESPONSE_VALUE);
            Log.i(TAG, "Extension response value override requested by automated tests: "
                    + extensionResponseValue);
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString(PREFERENCE_EXTENSION_RESPONSE_VALUE, extensionResponseValue)
                    .apply();
        }
        // Set policy override if specified
        if (mode == MODE_REJECT_WITH_POLICY_OVERRIDE) {
            int policyOverride = intent.getIntExtra(EXTRA_SESSION_VERIFICATION_POLICY_OVERRIDE,
                    DEVELOPER_VERIFICATION_POLICY_NONE);
            Log.i(TAG, "Policy override requested by automated tests: " + policyOverride);
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putBoolean(PREFERENCE_KEY_POLICY_OVERRIDE_ENABLED, true)
                    .apply();
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putInt(PREFERENCE_KEY_POLICY_OVERRIDE, policyOverride)
                    .apply();
            Log.i(TAG, "Override mode to REJECT");
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putInt(PREFERENCE_KEY_MODE, MODE_REJECT)
                    .apply();
        } else {
            // reset the policy override bit that was set previously
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putBoolean(PREFERENCE_KEY_POLICY_OVERRIDE_ENABLED, false)
                    .apply();
        }
        // Send back confirmation that the mode is set
        Intent broadcastIntent = new Intent(ACTION_MODE_SET);
        broadcastIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        broadcastIntent.putExtra(EXTRA_TEST_MODE, mode);
        sendBroadcast(broadcastIntent);

        finish();
    }
}
