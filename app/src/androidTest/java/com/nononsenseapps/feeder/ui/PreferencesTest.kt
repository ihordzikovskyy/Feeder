package com.nononsenseapps.feeder.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PreferencesTest {
    @Rule
    @JvmField
    var activityRule: ActivityTestRule<SettingsActivity> = ActivityTestRule(SettingsActivity::class.java)

    @Test
    fun activityStarts() {
        assertNotNull(activityRule.activity)
    }
}
