package io.github.rupakc.travelplanner.core.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.DefaultFeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProfileUiTest {
    @Test
    fun `every catalogue icon name has its own icon`() {
        DefaultFeatureCatalogue.all.forEach { assertNotEquals(it.iconName, Icons.Filled.Info, it.icon()) }
        assertEquals(Icons.Filled.Info, featureIcon("unknown"))
    }

    @Test
    fun `every feature and type has distinct title and description resources`() {
        val featureRes = FeatureId.entries.flatMap { listOf(it.titleRes(), it.descriptionRes()) }
        assertEquals(featureRes.size, featureRes.distinct().size)
        val typeRes = TravellerType.entries.flatMap { listOf(it.titleRes(), it.descriptionRes()) }
        assertEquals(typeRes.size, typeRes.distinct().size)
    }
}
