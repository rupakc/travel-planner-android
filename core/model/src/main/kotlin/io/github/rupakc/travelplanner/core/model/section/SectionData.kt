package io.github.rupakc.travelplanner.core.model.section

/**
 * Typed `data` of one results section. Every field is nullable/defaulted
 * (LLM output, api-contract §3.4). [error] is set when the section failed
 * (`{"error":"timed out"}`); keep the previous good data in that case.
 */
sealed interface SectionData {
    val error: String?
    val verification: Verification?

    val isError: Boolean get() = !error.isNullOrBlank()
}
