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

import static android.content.Intent.ACTION_INSTALL_PACKAGE;
import static android.content.pm.Flags.FLAG_VERIFICATION_SERVICE;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.content.pm.cts_root.utils.Constants.ACTION_INSTALLER_RESULT;
import static android.content.pm.cts_root.utils.Constants.ACTION_MODE_SET;
import static android.content.pm.cts_root.utils.Constants.ACTION_SET_TEST_MODE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_EXTENSION_RESPONSE_KEY;
import static android.content.pm.cts_root.utils.Constants.EXTRA_EXTENSION_RESPONSE_VALUE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_INSTALL_RESULT_INTENT;
import static android.content.pm.cts_root.utils.Constants.EXTRA_REJECT_MESSAGE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_SESSION_VERIFICATION_POLICY_OVERRIDE;
import static android.content.pm.cts_root.utils.Constants.EXTRA_TEST_MODE;
import static android.content.pm.cts_root.utils.Constants.MODE_PASS;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.cts_root.utils.Constants;
import android.net.Uri;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;
import android.util.Pair;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceConfigStateManager;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(Parameterized.class)
@RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
public abstract class DeveloperVerificationTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = DeveloperVerificationTestBase.class.getSimpleName();
    private static final String NAMESPACE_PACKAGE_MANAGER_SERVICE = "package_manager_service";
    private static final String PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS =
            "verification_request_timeout_millis";
    private static final String PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS =
            "verifier_connection_timeout_millis";
    private static final String VERIFIER_PACKAGE_NAME = "android.content.pm.cts_root.verifierapp";

    protected static final String REJECT_MESSAGE = "You shall not pass!";
    protected static final String EXTENSION_RESPONSE_KEY = "extension-response-key";
    protected static final String EXTENSION_RESPONSE_VALUE = "extension-response-value";

    @Parameter
    public static @PackageInstaller.DeveloperVerificationPolicy int sDefaultPolicy;

    /**
     * This test loops through all possible verification policies.
     */
    @Parameters
    public static Iterable<Object> initParameters() {
        return Arrays.asList(
                PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE,
                PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED
        );
    }

    // Default timeout to wait for results in this test
    static final long DEFAULT_TIMEOUT_MS =
            isLowRamDevice() ? TimeUnit.SECONDS.toMillis(60) : TimeUnit.SECONDS.toMillis(15);
    protected static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final PackageInstaller sPackageInstaller =
            sContext.getPackageManager().getPackageInstaller();
    private static final int sUserId = ActivityManager.getCurrentUser();

    private final MyBroadcastReceiver mBroadcastReceiver = new MyBroadcastReceiver();
    private static @PackageInstaller.DeveloperVerificationPolicy int sPreviousDefaultPolicy;
    private static String sPreviousRequestTimeoutMillis;
    private static String sPreviousConnectionTimeoutMillis;
    private CompletableFuture<Intent> mInstallerResult;
    private CompletableFuture<Integer> mVerifierModeSet;
    private CompletableFuture<Boolean> mInstallConfirmationReceived;

    @BeforeClass
    public static void setUpBeforeClass() {
        // Remember the previous default policy and restore it later
        sPreviousDefaultPolicy = getDefaultVerificationPolicy(sUserId);
        // Remember the timeout configs and restore them later.
        sPreviousRequestTimeoutMillis =
                getTimeoutValueFromDeviceConfig(PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS);
        sPreviousConnectionTimeoutMillis =
                getTimeoutValueFromDeviceConfig(PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS);
        // Change the system timeouts to shorter durations to reduce the test runtime.
        setTimeoutValueInDeviceConfig(
                PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS,
                String.valueOf(3000)); // Wait for 3 seconds before a request times out
        setTimeoutValueInDeviceConfig(
                PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS,
                String.valueOf(1000)); // Wait for 1 second for the connection to establish

        // Make sure that the test verifier is installed
        PackageManager pm = sContext.getPackageManager();
        PackageInfo pi = null;
        try {
            pi = pm.getPackageInfo(VERIFIER_PACKAGE_NAME, /* flags= */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Verifier app " + VERIFIER_PACKAGE_NAME + " is not installed");
        }
        assertThat(pi).isNotNull();
        assertThat(pi.applicationInfo).isNotNull();
        // Make sure that the test verifier is now the default verifier
        PackageInstaller packageInstaller = pm.getPackageInstaller();
        assertThat(packageInstaller.getDeveloperVerificationServiceProvider().getPackageName())
                .isEqualTo(VERIFIER_PACKAGE_NAME);
    }

    @Before
    public void setUp() {
        // Change the default verification policy
        setDefaultVerificationPolicy(sDefaultPolicy, sUserId);
        mInstallerResult = new CompletableFuture<>();
        mVerifierModeSet = new CompletableFuture<>();
        mInstallConfirmationReceived = new CompletableFuture<>();
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_INSTALLER_RESULT);
        intentFilter.addAction(ACTION_MODE_SET);
        sContext.registerReceiver(mBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
    }

    @After
    public void tearDown() {
        sContext.unregisterReceiver(mBroadcastReceiver);
    }

    @AfterClass
    public static void tearDownAfterClass() {
        setTimeoutValueInDeviceConfig(
                PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS, sPreviousRequestTimeoutMillis);
        setTimeoutValueInDeviceConfig(
                PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS, sPreviousConnectionTimeoutMillis);
        setDefaultVerificationPolicy(sPreviousDefaultPolicy, sUserId);
    }

    private void configureAndLaunchInstaller(String installerPackageName,
            @Nullable String extensionResponseKey) {
        // Start the installer app to install an empty test app and listen for results
        sContext.startActivity(
                createLaunchInstallerIntent(installerPackageName, extensionResponseKey));
    }

    private static Intent createLaunchInstallerIntent(String installerPackageName,
            @Nullable String extensionResponseKey) {
        Intent intent = new Intent("cts_root.action.LAUNCH_INSTALLER");
        intent.setPackage(installerPackageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        if (extensionResponseKey != null) {
            intent.putExtra(EXTRA_EXTENSION_RESPONSE_KEY, extensionResponseKey);
        }
        return intent;
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case ACTION_INSTALLER_RESULT:
                    final Intent resultIntent = intent.getParcelableExtra(
                            EXTRA_INSTALL_RESULT_INTENT, Intent.class);
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
                        SystemUtil.runWithShellPermissionIdentity(
                                () -> sPackageInstaller.setPermissionsResult(
                                        sessionId, /* accepted= */ true),
                                Manifest.permission.INSTALL_PACKAGES);
                        mInstallConfirmationReceived.complete(true);
                    } else {
                        // Otherwise, directly go to check the content of the result intent.
                        mInstallerResult.complete(resultIntent);
                    }
                    break;
                case ACTION_MODE_SET:
                    mVerifierModeSet.complete(intent.getIntExtra(EXTRA_TEST_MODE, MODE_PASS));
                    break;
                case null:
                default:
                    break;
            }
        }
    }

    private static boolean isLowRamDevice() {
        return InstrumentationRegistry.getInstrumentation()
                .getContext()
                .getSystemService(ActivityManager.class)
                .isLowRamDevice();
    }

    private void configureVerifier(@Constants.Mode int mode, @Nullable String rejectMessage,
            @Nullable String extensionResponseValue, int sessionPolicyOverride) throws Exception {
        Intent intent = new Intent(ACTION_SET_TEST_MODE);
        intent.setPackage(VERIFIER_PACKAGE_NAME);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_TEST_MODE, mode);
        if (rejectMessage != null) {
            intent.putExtra(EXTRA_REJECT_MESSAGE, rejectMessage);
        }
        if (extensionResponseValue != null) {
            intent.putExtra(EXTRA_EXTENSION_RESPONSE_VALUE, extensionResponseValue);
        }
        intent.putExtra(EXTRA_SESSION_VERIFICATION_POLICY_OVERRIDE, sessionPolicyOverride);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        sContext.startActivity(intent);
        // Wait to confirm that the mode is set by the verifier app
        assertThat(mVerifierModeSet.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isEqualTo(mode);
    }

    protected void installAndVerifyResults(TestParams testParams) throws Exception {
        final Pair<String, String> extensionResponseKeyValuePairExpected =
                testParams.getExtensionResponseKeyValuePairExpected();
        final String extensionResponseKeyExpected = extensionResponseKeyValuePairExpected.first;
        final String extensionResponseValueExpected = extensionResponseKeyValuePairExpected.second;
        // Then change the verifier mode and set its extension response values
        configureVerifier(testParams.getVerifierMode(), testParams.getVerifierRejectMessage(),
                extensionResponseValueExpected, testParams.getSessionPolicyOverride());
        // Launch the installer to install a package via the Session APIs
        configureAndLaunchInstaller(testParams.getInstallerPackageName(),
                extensionResponseKeyExpected);
        if (testParams.isInstallConfirmationExpected()) {
            // For a non-privileged installer, verify that the first user action requested was for
            // the install confirmation.
            assertThat(mInstallConfirmationReceived.get(
                    DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        }
        // Parse the installation result
        final Intent installerResultReceived = mInstallerResult.get(DEFAULT_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        final int statusCodeReceived = installerResultReceived.getIntExtra(
                PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
        final int sessionIdReceived = installerResultReceived.getIntExtra(
                PackageInstaller.EXTRA_SESSION_ID, Integer.MIN_VALUE);
        final int verificationFailureReasonReceived = installerResultReceived.getIntExtra(
                PackageInstaller.EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, Integer.MIN_VALUE);
        final String errorMessageReceived = installerResultReceived.getStringExtra(
                PackageInstaller.EXTRA_STATUS_MESSAGE);
        final PersistableBundle extensionResponseReceived =
                installerResultReceived.getParcelableExtra(
                        PackageInstaller.EXTRA_DEVELOPER_VERIFICATION_EXTENSION_RESPONSE,
                        PersistableBundle.class);
        final Intent userActionIntentReceived = installerResultReceived.getParcelableExtra(
                Intent.EXTRA_INTENT, Intent.class);
        // Verify the bundle received by the installer for the installation result
        assertThat(statusCodeReceived).isEqualTo(testParams.getStatusCodeExpected());
        assertThat(verificationFailureReasonReceived).isEqualTo(
                testParams.getVerificationFailureReasonExpected());
        if (testParams.getErrorMessageExpected() != null) {
            assertThat(errorMessageReceived).isEqualTo(testParams.getErrorMessageExpected());
        }
        if (testParams.hasUserActionIntentExpected()) {
            verifyUserActionIntentReceived(userActionIntentReceived, sessionIdReceived);
        }
        if (extensionResponseValueExpected != null) {
            assertThat(extensionResponseReceived).isNotNull();
            assertThat(extensionResponseReceived.getString(extensionResponseKeyExpected)).isEqualTo(
                    extensionResponseValueExpected);
        }
    }

    private static void verifyUserActionIntentReceived(
            Intent userActionIntentReceived, int sessionIdExpected) {
        assertThat(userActionIntentReceived).isNotNull();
        assertThat(userActionIntentReceived.getAction()).isEqualTo(
                PackageInstaller.ACTION_NOTIFY_DEVELOPER_VERIFICATION_INCOMPLETE);
        // Verify the content of the intent
        assertThat(userActionIntentReceived.getPackage()).isEqualTo(
                getPackageInstallerPackageName());
        assertThat(userActionIntentReceived.getIntExtra(
                PackageInstaller.EXTRA_SESSION_ID, PackageInstaller.SessionInfo.INVALID_ID))
                .isEqualTo(sessionIdExpected);
    }

    private static String getPackageInstallerPackageName() {
        PackageManager pm = sContext.getPackageManager();
        Intent installerIntent = new Intent(ACTION_INSTALL_PACKAGE);
        installerIntent.setDataAndType(
                Uri.parse("content://com.example/"), "application/vnd.android.package-archive");
        return installerIntent.resolveActivityInfo(pm, MATCH_DEFAULT_ONLY).packageName;
    }

    private static String getTimeoutValueFromDeviceConfig(String propertyName) {
        final DeviceConfigStateManager stateManager =
                new DeviceConfigStateManager(
                        sContext, NAMESPACE_PACKAGE_MANAGER_SERVICE, propertyName);
        return stateManager.get();
    }

    private static void setTimeoutValueInDeviceConfig(String propertyName, String value) {
        final DeviceConfigStateManager stateManager =
                new DeviceConfigStateManager(
                        sContext, NAMESPACE_PACKAGE_MANAGER_SERVICE, propertyName);
        String currentValue = stateManager.get();
        if (!Objects.equals(currentValue, value)) {
            // Only change the value if the current value is different
            stateManager.set(value);
        }
    }

    private static int getDefaultVerificationPolicy(int userId) {
        String policyStr =
                SystemUtil.runShellCommand("pm get-developer-verification-policy --user "
                        + userId).trim();
        return Integer.parseInt(policyStr);
    }

    private static void setDefaultVerificationPolicy(
            @PackageInstaller.DeveloperVerificationPolicy int policy, int userId) {
        SystemUtil.runShellCommand(
                "pm set-developer-verification-policy --user " + userId + " " + policy);
        assertThat(getDefaultVerificationPolicy(userId)).isEqualTo(policy);
    }

    protected static class TestParams {
        private final String mInstallerPackageName;
        @Constants.Mode
        private final int mVerifierMode;
        private final String mVerifierRejectMessage;
        private final int mStatusCodeExpected;
        private final int mVerificationFailureReasonExpected;
        private final String mErrorMessageExpected;
        private final Pair<String, String> mExtensionResponseKeyValuePairExpected;
        private final boolean mHasUserActionIntentExpected;
        private final @PackageInstaller.DeveloperVerificationPolicy int mSessionPolicyOverride;
        private final boolean mIsInstallConfirmationExpected;

        protected TestParams(String installerPackageName, int verifierMode,
                String verifierRejectMessage, int statusCodeExpected,
                int verificationFailureReasonExpected, String errorMessageExpected,
                Pair<String, String> extensionResponseKeyValuePairExpected,
                boolean hasUserActionIntentExpected, int sessionPolicyOverride,
                boolean isInstallConfirmationExpected) {
            mInstallerPackageName = installerPackageName;
            mVerifierMode = verifierMode;
            mVerifierRejectMessage = verifierRejectMessage;
            mStatusCodeExpected = statusCodeExpected;
            mVerificationFailureReasonExpected = verificationFailureReasonExpected;
            mErrorMessageExpected = errorMessageExpected;
            mExtensionResponseKeyValuePairExpected = extensionResponseKeyValuePairExpected;
            mHasUserActionIntentExpected = hasUserActionIntentExpected;
            mSessionPolicyOverride = sessionPolicyOverride;
            mIsInstallConfirmationExpected = isInstallConfirmationExpected;
        }

        public String getInstallerPackageName() {
            return mInstallerPackageName;
        }

        public int getVerifierMode() {
            return mVerifierMode;
        }
        public String getVerifierRejectMessage() {
            return mVerifierRejectMessage;
        }

        public int getStatusCodeExpected() {
            return mStatusCodeExpected;
        }

        public int getVerificationFailureReasonExpected() {
            return mVerificationFailureReasonExpected;
        }

        public String getErrorMessageExpected() {
            return mErrorMessageExpected;
        }

        public Pair<String, String> getExtensionResponseKeyValuePairExpected() {
            return mExtensionResponseKeyValuePairExpected;
        }

        public boolean hasUserActionIntentExpected() {
            return mHasUserActionIntentExpected;
        }

        public int getSessionPolicyOverride() {
            return mSessionPolicyOverride;
        }

        public boolean isInstallConfirmationExpected() {
            return mIsInstallConfirmationExpected;
        }

        static class TestParamsBuilder {
            private String mInstallerPackageName = null;
            @Constants.Mode
            private int mVerifierMode = MODE_PASS;
            private String mVerifierRejectMessage = null;
            private int mStatusCodeExpected = Integer.MIN_VALUE;
            private int mVerificationFailureReasonExpected = Integer.MIN_VALUE;
            private String mErrorMessageExpected = null;
            private Pair<String, String> mExtensionResponseKeyValuePairExpected =
                    new Pair<>(null, null);
            private boolean mHasUserActionIntentExpected = false;
            private @PackageInstaller.DeveloperVerificationPolicy int mSessionPolicyOverride =
                    sDefaultPolicy;

            private boolean mIsInstallConfirmationExpected = false;

            public TestParamsBuilder setInstallerPackageName(String installerPackageName) {
                this.mInstallerPackageName = installerPackageName;
                return this;
            }

            public TestParamsBuilder setVerifierMode(@Constants.Mode int verifierMode) {
                this.mVerifierMode = verifierMode;
                return this;
            }

            public TestParamsBuilder setVerifierRejectMessage(String errorMessage) {
                this.mVerifierRejectMessage = errorMessage;
                return this;
            }


            public TestParamsBuilder setStatusCodeExpected(int statusCodeExpected) {
                this.mStatusCodeExpected = statusCodeExpected;
                return this;
            }

            public TestParamsBuilder setVerificationFailureReasonExpected(
                    int verificationFailureReasonExpected) {
                this.mVerificationFailureReasonExpected = verificationFailureReasonExpected;
                return this;
            }

            public TestParamsBuilder setErrorMessageExpected(String errorMessageExpected) {
                this.mErrorMessageExpected = errorMessageExpected;
                return this;
            }

            public TestParamsBuilder setExtensionResponseKeyValuePairExpected(
                    Pair<String, String> extensionResponseKeyValuePairExpected) {
                this.mExtensionResponseKeyValuePairExpected = extensionResponseKeyValuePairExpected;
                return this;
            }

            public TestParamsBuilder setHasUserActionIntentExpected(
                    boolean hasUserActionIntentExpected) {
                this.mHasUserActionIntentExpected = hasUserActionIntentExpected;
                return this;
            }

            public TestParamsBuilder setSessionVerificationPolicyOverride(
                    @PackageInstaller.DeveloperVerificationPolicy int policyOverride) {
                this.mSessionPolicyOverride = policyOverride;
                return this;
            }

            public TestParamsBuilder setIsInstallConfirmationExpected(
                    boolean isInstallConfirmationExpected) {
                this.mIsInstallConfirmationExpected = isInstallConfirmationExpected;
                return this;
            }

            public TestParams build() {
                return new TestParams(this.mInstallerPackageName,
                        this.mVerifierMode, this.mVerifierRejectMessage, this.mStatusCodeExpected,
                        this.mVerificationFailureReasonExpected, this.mErrorMessageExpected,
                        this.mExtensionResponseKeyValuePairExpected,
                        this.mHasUserActionIntentExpected,
                        this.mSessionPolicyOverride, this.mIsInstallConfirmationExpected);
            }
        }
    }
}
