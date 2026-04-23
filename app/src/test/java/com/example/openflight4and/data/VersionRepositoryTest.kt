package com.example.openflight4and.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionRepositoryTest {

    private val repository = VersionRepository { _, _ -> }

    @Test
    fun parseVersionCode_convertsStableVersionNameToComparableCode() {
        assertEquals(208070000, repository.parseVersionCode("V2.8.7"))
        assertEquals(208080000, repository.parseVersionCode("2.8.8"))
    }

    @Test
    fun parseVersionCode_convertsBetaVersionNameToComparableCode() {
        assertEquals(208070001, repository.parseVersionCode("V2.8.7.Beta.0001"))
    }

    @Test
    fun compareVersions_ordersStableAndBetaBuildsCorrectly() {
        assertEquals(-1, repository.compareVersions("V2.8.7", "V2.8.7.Beta.0001"))
        assertEquals(-1, repository.compareVersions("V2.8.7.Beta.0001", "V2.8.8"))
    }

    @Test
    fun determineRequirement_returnsRecommendedForOutdatedStableBuild() {
        assertEquals(
            UpdateRequirement.RECOMMENDED,
            repository.determineRequirement(
                releaseChannel = "stable",
                currentVersionCode = 208070000,
                allowedVersion = "V2.8.7",
                recentVersion = "V2.8.8"
            )
        )
    }

    @Test
    fun determineRequirement_skipsRecommendedUpdateForBetaBuild() {
        assertEquals(
            UpdateRequirement.NONE,
            repository.determineRequirement(
                releaseChannel = "beta",
                currentVersionCode = 208070001,
                allowedVersion = "V2.8.7",
                recentVersion = "V2.8.8"
            )
        )
    }

    @Test
    fun determineRequirement_keepsRequiredUpdateForTooOldBetaBuild() {
        assertEquals(
            UpdateRequirement.REQUIRED,
            repository.determineRequirement(
                releaseChannel = "beta",
                currentVersionCode = 208060001,
                allowedVersion = "V2.8.7",
                recentVersion = "V2.8.8"
            )
        )
    }
}
