package org.webosarchive.lunacy.card

import android.content.pm.PackageManager
import android.view.InputDevice
import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A remote's keys are taken on a TV alone: a phone's or tablet's D-pad keeps its keys (codepoet, 2026-11-23). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RemoteKeysTest {
    private fun dpad(code: Int) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, 0, -1, 0, 0, InputDevice.SOURCE_DPAD)

    private fun leanback(on: Boolean) {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, on)
        RemoteKeys.init(app)
    }

    @Test fun notOnAPhoneOrTablet() {
        leanback(false)
        for (code in listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER))
            assertFalse(RemoteKeys.fromRemote(dpad(code)))
    }

    @Test fun onATv() {
        leanback(true)
        assertTrue(RemoteKeys.fromRemote(dpad(KeyEvent.KEYCODE_DPAD_DOWN)))
        assertTrue(RemoteKeys.fromRemote(dpad(KeyEvent.KEYCODE_DPAD_CENTER)))
        // Not a key the remote moves the focus with.
        assertFalse(RemoteKeys.fromRemote(dpad(KeyEvent.KEYCODE_A)))
        leanback(false)
    }
}
