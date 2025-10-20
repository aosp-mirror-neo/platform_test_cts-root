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

/**
 * The goal of this test is to make sure that the verifier works fine on a device with work profile.
 * For this test we use the privileged version of the installer targeting 37. We need the privileged
 * version of the installer because it needs to send broadcasts across users.
 */
public class DeveloperVerificationWorkProfileTest extends DeveloperVerificationMultiUserTestBase {
    @Override
    protected int createUser(String name) throws Exception {
        // Must use a profile so the installer activity can be launched from the current user
        final String output = mUiDevice.executeShellCommand(
                "pm create-user --profileOf 0 --user-type android.os.usertype.profile.MANAGED "
                        + name);
        if (output.startsWith("Success")) {
            return Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
        }
        throw new IllegalStateException(String.format("Failed to create user: %s", output));
    }
}
