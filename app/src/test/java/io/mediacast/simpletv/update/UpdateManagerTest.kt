package io.mediacast.simpletv.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test
    fun comparesNumericVersionSegments() {
        assertTrue(UpdateManager.isNewer("1.3.0", "1.2.0"))
        assertTrue(UpdateManager.isNewer("v1.2.1", "1.2.0"))
        assertTrue(UpdateManager.isNewer("1.2.0", "1.1.9"))
        assertFalse(UpdateManager.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateManager.isNewer("1.1.9", "1.2.0"))
    }

    @Test
    fun ignoresPrereleaseSuffixForComparison() {
        assertTrue(UpdateManager.isNewer("1.3.0-beta", "1.2.0"))
        assertFalse(UpdateManager.isNewer("1.2.0-beta", "1.2.0"))
    }
}
