package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.share.CreatedShare
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.model.share.ShareOptions
import io.github.rupakc.travelplanner.core.model.share.SharesList
import java.io.File

/**
 * Share links 2.0. Callers must have checked `JourneyFlags.sharing`. Failures carry the server's `error_code`; read it with
 * `AppError.shareErrorCode()`.
 */
interface SharesRepository {
    /**
     * `POST /shares`, idempotent per (kind, plan): the same token comes back and its [options] are replaced. [planId] is
     * required for `plan`, `recap` and `status`; [sessionId] for `quiz`. The page language is the UI language. `404 plan_not_found|sharing_disabled`,
     * `422 invalid_kind`, 429.
     */
    suspend fun create(kind: ShareKind, planId: Long?, options: ShareOptions, sessionId: String? = null): AppResult<CreatedShare>

    /** `GET /shares`: own shares newest first plus the legacy plan shares. */
    suspend fun list(): AppResult<SharesList>

    /** `DELETE /shares/{token}`. `404 share_not_found` when the token is not the caller's. */
    suspend fun revoke(token: String): AppResult<Unit>
}

/**
 * Downloads a share card PNG (public, no token sent) into the app cache so it can be handed to the system share sheet
 * through a `FileProvider`. Only one card is kept: a new download replaces the previous file, and sign-out clears it.
 */
interface ShareCardStore {
    /** The card at [url] as a file; a failure is [io.github.rupakc.travelplanner.core.common.AppError.Network] (share the link only) or similar. */
    suspend fun download(url: String): AppResult<File>
}
