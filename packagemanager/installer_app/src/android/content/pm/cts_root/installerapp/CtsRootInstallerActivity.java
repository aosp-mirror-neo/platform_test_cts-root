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

package android.content.pm.cts_root.installerapp;

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.content.pm.PackageInstaller.EXTRA_STATUS;
import static android.content.pm.PackageInstaller.STATUS_FAILURE;
import static android.content.pm.cts_root.utils.Constants.ACTION_INSTALLER_RESULT;
import static android.content.pm.cts_root.utils.Constants.EXTRA_EXTENSION_RESPONSE_KEY;
import static android.content.pm.cts_root.utils.Constants.EXTRA_INSTALL_APK_PATH;
import static android.content.pm.cts_root.utils.Constants.EXTRA_INSTALL_PACKAGE_NAME;
import static android.content.pm.cts_root.utils.Constants.EXTRA_INSTALL_RESULT_INTENT;
import static android.content.pm.cts_root.utils.Constants.EXTRA_IS_SPLIT_INSTALL;
import static android.content.pm.cts_root.utils.Constants.EXTRA_TEST_USER;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CtsRootInstallerActivity extends Activity {
    private static final String TAG = CtsRootInstallerActivity.class.getSimpleName();
    private static UserHandle sTestUser = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UserHandle myUserHandle = Process.myUserHandle();
        Log.i(TAG, "Installer is launched on user " + myUserHandle);

        boolean isSplitInstall = getIntent().getBooleanExtra(EXTRA_IS_SPLIT_INSTALL, false);
        PackageInstaller.SessionParams params;
        if (!isSplitInstall) {
            params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        } else {
            params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_INHERIT_EXISTING);
            params.setDontKillApp(true);
        }

        String packageName = getIntent().getStringExtra(EXTRA_INSTALL_PACKAGE_NAME);
        if (packageName != null) {
            params.setAppPackageName(packageName);
        }

        String extensionResponseKey = getIntent().getStringExtra(EXTRA_EXTENSION_RESPONSE_KEY);
        if (extensionResponseKey != null) {
            // Insert extension params with empty values
            PersistableBundle extensionParams = new PersistableBundle();
            extensionParams.putString(extensionResponseKey, "");
            params.setExtensionParams(extensionParams);
        }

        String apkPath = getIntent().getStringExtra(EXTRA_INSTALL_APK_PATH);

        sTestUser = getIntent().getParcelableExtra(EXTRA_TEST_USER, UserHandle.class);

        PackageInstaller pi = getApplicationContext().getPackageManager().getPackageInstaller();
        try {
            final int sessionId = pi.createSession(params);
            PackageInstaller.Session session = pi.openSession(sessionId);
            writeFileToSession(session, apkPath, isSplitInstall);
            LocalIntentSender sender = new LocalIntentSender();
            session.commit(sender.getIntentSender());
            Log.i(TAG, "Started app installation on user " + myUserHandle
                    + " sessionID=" + sessionId);
        } catch (Exception e) {
            Log.i(TAG, "Failed to install empty app", e);
        }
    }

    private class LocalIntentSender extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(EXTRA_STATUS,
                    /* defaultValue= */ STATUS_FAILURE);
            Log.i(TAG, "Received installation result of " + status);
            // Forward result intent to the main test to verify its content or bypass the user
            // confirmation dialog.
            Intent i = new Intent(ACTION_INSTALLER_RESULT);
            i.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            i.putExtra(EXTRA_INSTALL_RESULT_INTENT, intent);
            // The test might be running on a different user than the installer itself
            Log.i(TAG, "Sending installation result to " + sTestUser);
            sendBroadcastAsUser(i, sTestUser);
            // Don't finish listening if the result is for install confirmation
            // TODO: think of a better way to finish when it should
            final Intent extraIntent = intent.getParcelableExtra(
                    Intent.EXTRA_INTENT, Intent.class);
            if (extraIntent == null
                    || !"android.content.pm.action.CONFIRM_INSTALL".equals(
                            extraIntent.getAction())) {
                getApplicationContext().unregisterReceiver(this);
                finish();
            }
        }

        public IntentSender getIntentSender() {
            Context context = getApplicationContext();
            // Generate a unique string to ensure each LocalIntentSender gets its own results.
            String action = LocalIntentSender.class.getName() + SystemClock.elapsedRealtime();
            context.registerReceiver(this, new IntentFilter(action),
                    Context.RECEIVER_EXPORTED);
            Intent intent = new Intent(action).setPackage(context.getPackageName())
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent, FLAG_MUTABLE);
            return pending.getIntentSender();
        }
    }

    private static void writeFullStream(InputStream inputStream, OutputStream outputStream)
            throws IOException {
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
    }

    private static void writeFileToSession(PackageInstaller.Session session, String apkPath,
            boolean isSplitInstall) throws IOException {
        final File apkFile = new File(apkPath);
        try (
                OutputStream os = session.openWrite(
                        isSplitInstall ? "split_config.mdpi" : "base.apk", 0, apkFile.length());
                InputStream is = new FileInputStream(apkFile)) {
            writeFullStream(is, os);
        }
    }
}
