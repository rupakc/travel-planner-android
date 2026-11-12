package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.domain.auth.OauthCallback
import io.github.rupakc.travelplanner.core.model.auth.OauthResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Process-scoped hand-over between the Activity (which sees `voyager://auth/callback`) and whichever screen
 * started the attempt, plus the `needs_username` draft between Login and the username screen. In memory only.
 */
@Singleton
class OauthInbox
    @Inject
    constructor() {
        private val channel = Channel<OauthCallback>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        private val mutableDraft = MutableStateFlow<OauthResult?>(null)

        /** Callbacks in arrival order; one collector receives each. */
        val callbacks: Flow<OauthCallback> = channel.receiveAsFlow()

        /** A `needs_username` result waiting for the username screen (carries the single-use `signup_token`). */
        val draft: StateFlow<OauthResult?> = mutableDraft.asStateFlow()

        fun post(callback: OauthCallback) {
            channel.trySend(callback)
        }

        fun setDraft(result: OauthResult?) {
            mutableDraft.value = result
        }
    }
