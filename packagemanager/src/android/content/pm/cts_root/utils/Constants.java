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

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class Constants {
    // Action shared between the main test and the installer for communicating installer result
    public static final String ACTION_INSTALLER_RESULT =
            "android.content.pm.cts_root.INSTALLER_RESULT";
    public static final String EXTRA_INSTALL_RESULT_INTENT = "extra-intent";

    // Action and extras shared between the main test and the verifier for setting verifier modes
    public static final String ACTION_SET_TEST_MODE =
            "android.content.pm.cts_root.SET_VERIFIER_MODE";
    public static final String EXTRA_TEST_MODE = "extra-test-mode";
    public static final String EXTRA_SESSION_VERIFICATION_POLICY_OVERRIDE = "extra-policy-override";
    public static final String ACTION_MODE_SET = "android.content.pm.cts_root.MODE_SET";
    public static final String EXTRA_REJECT_MESSAGE = "extra-reject-message";
    // Test -> Installer
    public static final String EXTRA_EXTENSION_RESPONSE_KEY = "extra-extension-response-key";
    public static final String EXTRA_INSTALL_APK_PATH = "extra-install-apk-path";
    public static final String EXTRA_IS_SPLIT_INSTALL = "extra-is-split-install";
    public static final String EXTRA_INSTALL_PACKAGE_NAME = "extra-install-package-name";
    public static final String EXTRA_TEST_USER = "extra-test-user";
    // Test -> Verifier
    public static final String EXTRA_EXTENSION_RESPONSE_VALUE = "extra-extension-response-value";
    public static final String ACTION_QUERY_DELEGATE = "android.content.pm.cts_root.QUERY_DELEGATE";
    public static final String ACTION_REPORT_QUERY_DELEGATE_RESULT =
            "android.content.pm.cts_root.QUERY_DELEGATE_RESULT";
    public static final String EXTRA_DELEGATE_PACKAGE_NAME = "extra-delegate-package-name";
    public static final String ACTION_SET_POLICY = "android.content.pm.cts_root.SET_POLICY";
    public static final String ACTION_REPORT_POLICY_SET_RESULT =
            "android.content.pm.cts_root.REPORT_POLICY_SET_RESULT";
    public static final String EXTRA_POLICY_VALUE = "extra-policy-value";

    // Constants for verifier modes
    public static final int MODE_PASS = 0;
    public static final int MODE_INCOMPLETE_NETWORK_UNAVAILABLE = 1;
    public static final int MODE_INCOMPLETE_UNKNOWN = 2;
    public static final int MODE_REJECT = 3;
    public static final int MODE_REJECT_WITH_EXTENSION_RESPONSE = 4;
    public static final int MODE_REJECT_WITH_POLICY_OVERRIDE = 5;
    public static final int MODE_TIMEOUT = 6;
    public static final int MODE_CRASH = 7;
    public static final int MODE_LITE = 8;
    public static final int MODE_BYPASS = 9;

    @IntDef(flag = true, prefix = { "MODE_" }, value = {
            MODE_PASS,
            MODE_INCOMPLETE_NETWORK_UNAVAILABLE,
            MODE_INCOMPLETE_UNKNOWN,
            MODE_REJECT,
            MODE_REJECT_WITH_EXTENSION_RESPONSE,
            MODE_REJECT_WITH_POLICY_OVERRIDE,
            MODE_TIMEOUT,
            MODE_CRASH,
            MODE_LITE,
            MODE_BYPASS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {}


    public static final String VERIFIER_PACKAGE_NAME = "android.content.pm.cts_root.verifierapp";
    public static final String DELEGATE_PACKAGE_NAME = "android.content.pm.cts_root.delegateapp";
    public static final String INSTALLER_PACKAGE_NAME = "android.content.pm.cts_root.installerapp";
    public static final String EMERGENCY_INSTALLER_PACKAGE_NAME =
            "android.content.pm.cts_root.emergencyinstallerapp";
    public static final String PRIV_INSTALLER_PACKAGE_NAME =
            "android.content.pm.cts_root.privinstallerapp";
    public static final String APK_PATH_BASE = "/data/local/tmp/cts-root/";
    public static final String EMPTY_TEST_APK = APK_PATH_BASE + "CtsRootEmptyTestApp.apk";
    public static final String EMPTY_TEST_PACKAGE_NAME = "android.content.pm.cts_root.emptytestapp";
}
