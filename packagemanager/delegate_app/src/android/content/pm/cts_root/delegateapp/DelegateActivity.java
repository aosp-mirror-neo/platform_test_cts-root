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

package android.content.pm.cts_root.delegateapp;

import static android.content.pm.cts_root.utils.Constants.ACTION_REPORT_POLICY_SET_RESULT;
import static android.content.pm.cts_root.utils.Constants.ACTION_SET_POLICY;
import static android.content.pm.cts_root.utils.Constants.EXTRA_POLICY_VALUE;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.util.Log;

public class DelegateActivity extends Activity {
    private static final String TAG = DelegateActivity.class.getSimpleName();
    private static final int INVALID_POLICY = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (ACTION_SET_POLICY.equals(intent.getAction())) {
            int policy = intent.getIntExtra(EXTRA_POLICY_VALUE, INVALID_POLICY);
            if (policy == INVALID_POLICY) {
                Log.i(TAG, "Received request to change the policy but the policy is invalid");
                finish();
            }
            Log.i(TAG, "Received request to change the policy to " + policy);
            PackageInstaller pi = getApplicationContext().getPackageManager().getPackageInstaller();
            try {
                if (!pi.setDeveloperVerificationPolicy(policy)) {
                    Log.e(TAG, "Failed to set the policy to " + policy);
                } else {
                    Log.i(TAG, "Successfully set the policy to " + policy);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set the policy because of exception", e);
            }
            // Report the current policy to the test even if the policy wasn't set successfully.
            Intent broadcastIntent = new Intent(ACTION_REPORT_POLICY_SET_RESULT);
            broadcastIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            broadcastIntent.putExtra(EXTRA_POLICY_VALUE, pi.getDeveloperVerificationPolicy());
            sendBroadcast(broadcastIntent);
        }
        finish();
    }
}
