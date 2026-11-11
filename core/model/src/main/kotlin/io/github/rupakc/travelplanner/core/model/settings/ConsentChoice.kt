package io.github.rupakc.travelplanner.core.model.settings

/** The user's explicit telemetry consent choice; [wire] is the value sent in the `consent` event. */
enum class ConsentChoice(val wire: String) {
    /** The user allowed anonymous usage telemetry. */
    GRANTED("granted"),

    /** The user declined telemetry; stored only on the device. */
    DENIED("denied"),
}
