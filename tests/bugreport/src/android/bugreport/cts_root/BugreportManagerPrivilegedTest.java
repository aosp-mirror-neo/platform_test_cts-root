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

package android.bugreport.cts_root;

import static android.os.Flags.FLAG_ALLOW_CONSENTLESS_BUGREPORT_DELEGATED_CONSENT;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.content.Context;
import android.os.BugreportManager;
import android.os.BugreportManager.BugreportCallback;
import android.os.BugreportParams;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Device-side tests for Bugreport Manager API, the test runs in privileged application with
 * permission CAPTURE_CONSENTLESS_BUGREPORT_DELEGATED_CONSENT.
 *
 * <p>These tests require root to allowlist the test package to use the BugreportManager APIs.
 */
@RunWith(AndroidJUnit4.class)
public class BugreportManagerPrivilegedTest {

    private Context mContext;
    private BugreportManager mBugreportManager;
    private Method mGetServiceMethod;

    @Rule public TestName name = new TestName();

    private static final long UIAUTOMATOR_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mBugreportManager = mContext.getSystemService(BugreportManager.class);
        mGetServiceMethod =
                Class.forName("android.os.ServiceManager").getMethod("getService", String.class);

        // Force remove consent-dialog by killing IncidentActivity
        runShellCommand("am force-stop com.android.permissioncontroller");
    }

    @BeforeClass
    public static void classSetup() {
        // Kill current bugreport, so that it does not interfere with future bugreports.
        runShellCommand("setprop ctl.stop bugreportd");
    }

    @AfterClass
    public static void classTearDown() {
        // Kill current bugreport, so that it does not interfere with future bugreports.
        runShellCommand("setprop ctl.stop bugreportd");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ALLOW_CONSENTLESS_BUGREPORT_DELEGATED_CONSENT)
    public void testConsentlessBugreport_skipsConsentIfHavePermission() throws Exception {
        try {
            File bugreportFile = createTempFile("startbugreport", ".zip");
            CountDownLatch latch = new CountDownLatch(1);
            BugreportCallbackImpl callback = new BugreportCallbackImpl(latch);

            mBugreportManager.startBugreport(
                    parcelFd(bugreportFile),
                    null,
                    new BugreportParams(BugreportParams.BUGREPORT_MODE_FULL),
                    mContext.getMainExecutor(),
                    callback);

            callback.waitForUiReady();

            assertConsentDialogStatus(false);
            latch.await(4, TimeUnit.MINUTES);
            assertThat(callback.isSuccess()).isTrue();
            assertThat(bugreportFile.length()).isGreaterThan(0);
        } finally {
            waitForDumpstateServiceToStop();
            // Remove all bugreport files
            SystemUtil.runShellCommand("rm -f -rR -v /bugreports/");
        }
    }

    private ParcelFileDescriptor parcelFd(File file) throws Exception {
        return ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND);
    }

    private static File createTempFile(String prefix, String extension) throws Exception {
        final File f = File.createTempFile(prefix, extension);
        f.setReadable(true, true);
        f.setWritable(true, true);

        f.deleteOnExit();
        return f;
    }

    private static final class BugreportCallbackImpl extends BugreportCallback {
        private int mErrorCode = -1;
        private boolean mSuccess = false;
        private String mBugreportFile;
        private final Object mLock = new Object();
        private final CountDownLatch mUiReadyLatch = new CountDownLatch(1);

        private final CountDownLatch mLatch;

        BugreportCallbackImpl(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onError(int errorCode) {
            synchronized (mLock) {
                mErrorCode = errorCode;
                mLatch.countDown();
            }
        }

        @Override
        public void onEarlyReportFinished() {
            mUiReadyLatch.countDown();
        }

        /**
         * Wait for onEarlyReportFinished to be called. If this invocation of startBugreport
         * requires user consent, the dialog will show then; otherwise, it won't.
         */
        public void waitForUiReady() throws Exception {
            mUiReadyLatch.await(30, TimeUnit.SECONDS);
        }

        @Override
        public void onFinished(String bugreportFile) {
            synchronized (mLock) {
                mBugreportFile = bugreportFile;
                mLatch.countDown();
                mSuccess = true;
            }
        }

        @Override
        public void onFinished() {
            synchronized (mLock) {
                mLatch.countDown();
                mSuccess = true;
            }
        }

        public int getErrorCode() {
            synchronized (mLock) {
                return mErrorCode;
            }
        }

        public boolean isSuccess() {
            synchronized (mLock) {
                return mSuccess;
            }
        }

        public String getBugreportFile() {
            synchronized (mLock) {
                return mBugreportFile;
            }
        }
    }

    private void assertConsentDialogStatus(boolean expectShown) throws Exception {
        final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        final BySelector consentTitleObj = By.res("android", "alertTitle");
        // Consent dialog is bring up by Activity, may delay after onEarlyReportFinished,
        // we test 5 times with UIAUTOMATOR_TIMEOUT_MS (10s) to make sure we does not do this
        // examination too early.
        boolean hasDialog = false;
        for (int i = 0; i < 5; i++) {
            hasDialog = device.wait(Until.hasObject(consentTitleObj), UIAUTOMATOR_TIMEOUT_MS);
            if (hasDialog) {
                break;
            }
        }
        if (!hasDialog && expectShown) {
            fail("Expect consent dialog shows up but didn't found dialog.");
        } else if (hasDialog && !expectShown) {
            fail("Expect no consent dialog shows up but found dialog.");
        }
    }

    /** Waits for the dumpstate service to stop, for up to 5 seconds. */
    private boolean isDumpstateServiceStopped() throws Exception {
        // If getService() returns null, the service has stopped.
        return ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mGetServiceMethod,
                        (m) -> {
                            try {
                                return m.invoke(null, "dumpstate");
                            } catch (Exception e) {
                                return null;
                            }
                        })
                == null;
    }

    private void waitForDumpstateServiceToStop() throws Exception {
        int pollingIntervalMillis = 100;

        for (int i = 0; i < 10; i++) {
            int numPolls = 50;
            while (numPolls-- > 0) {
                if (isDumpstateServiceStopped()) {
                    break;
                }
                Thread.sleep(pollingIntervalMillis);
            }
        }
        if (isDumpstateServiceStopped()) {
            return;
        }
        fail("Dumpstate did not stop within 25 seconds");
    }
}
