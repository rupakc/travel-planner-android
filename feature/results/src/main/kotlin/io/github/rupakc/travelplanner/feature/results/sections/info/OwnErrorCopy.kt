package io.github.rupakc.travelplanner.feature.results.sections.info

import io.github.rupakc.travelplanner.feature.results.sections.ResultsSection

/**
 * Sections whose content composable renders the web's own copy for an error result
 * (`{"error": …}`) instead of the generic ErrorState:
 * Free walking tours ("Couldn't reach the walking-tour sites…"), What's On ("Couldn't check local events…"),
 * Trip Health Check ("Health check unavailable for this plan…") and Forex ("Currency info unavailable…").
 *
 * SectionRegistry: route `data.isError && section.rendersOwnError` to `SectionContent(section, data, actions)`
 * and keep `ErrorState` + retry for the others.
 */
val ResultsSection.rendersOwnError: Boolean
    get() = this in OWN_ERROR_SECTIONS

private val OWN_ERROR_SECTIONS =
    setOf(ResultsSection.WalkingTours, ResultsSection.Events, ResultsSection.StressTest, ResultsSection.Forex)
