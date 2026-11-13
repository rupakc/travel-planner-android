package io.github.rupakc.travelplanner.feature.social.feed

import io.github.rupakc.travelplanner.core.model.social.Idea
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A change made on the idea screen that an open feed list must follow. */
internal sealed interface IdeaChange {
    /** Counts or like/save marks changed. */
    data class Updated(val idea: Idea) : IdeaChange

    data class Deleted(val id: Long) : IdeaChange

    /** All ideas of this author leave the list (the pair is blocked now). */
    data class AuthorBlocked(val handle: String) : IdeaChange
}

/** Process-wide bus between the idea screen and the feed list; nothing is kept for late subscribers. */
@Singleton
internal class IdeaChanges
    @Inject
    constructor() {
        private val flow = MutableSharedFlow<IdeaChange>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val changes: SharedFlow<IdeaChange> = flow.asSharedFlow()

        fun publish(change: IdeaChange) {
            flow.tryEmit(change)
        }

        private companion object {
            const val BUFFER = 16
        }
    }
