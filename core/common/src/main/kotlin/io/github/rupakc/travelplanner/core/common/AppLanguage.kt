package io.github.rupakc.travelplanner.core.common

/** The effective UI language: always one of en, es, de, fr, hi, zh, bn ("en" when the device language is unsupported). */
interface AppLanguage {
    /** The language code in effect now. */
    fun current(): String
}
