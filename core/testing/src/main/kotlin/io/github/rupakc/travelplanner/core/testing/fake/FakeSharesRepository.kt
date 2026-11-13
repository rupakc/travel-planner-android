package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.ShareCardStore
import io.github.rupakc.travelplanner.core.data.repository.SharesRepository
import io.github.rupakc.travelplanner.core.model.share.CreatedShare
import io.github.rupakc.travelplanner.core.model.share.ShareCardUrls
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.model.share.ShareOptions
import io.github.rupakc.travelplanner.core.model.share.SharesList
import java.io.File

/** Scriptable [SharesRepository]: set the `*Result` of the call under test. Calls are recorded. */
class FakeSharesRepository : SharesRepository {
    var createResult: AppResult<CreatedShare> =
        AppResult.Success(
            CreatedShare(
                token = "tok123",
                kind = "plan",
                url = "https://example.test/share/tok123",
                cardUrl =
                    ShareCardUrls(
                        link = "https://example.test/api/share/tok123/card.png?size=link",
                        story = "https://example.test/api/share/tok123/card.png?size=story",
                    ),
            ),
        )
    var listResult: AppResult<SharesList> = AppResult.Success(SharesList())
    var revokeResult: AppResult<Unit> = AppResult.Success(Unit)

    data class CreateCall(val kind: ShareKind, val planId: Long?, val options: ShareOptions, val sessionId: String? = null)

    val createCalls = mutableListOf<CreateCall>()
    val revoked = mutableListOf<String>()

    override suspend fun create(kind: ShareKind, planId: Long?, options: ShareOptions, sessionId: String?): AppResult<CreatedShare> =
        createResult.also { createCalls += CreateCall(kind, planId, options, sessionId) }

    override suspend fun list(): AppResult<SharesList> = listResult

    override suspend fun revoke(token: String): AppResult<Unit> = revokeResult.also { revoked += token }
}

/** [ShareCardStore] answering [result] (a failure by default); the requested URLs are recorded. */
class FakeShareCardStore(var result: AppResult<File> = AppResult.Failure(AppError.Network(null))) : ShareCardStore {
    val downloads = mutableListOf<String>()

    override suspend fun download(url: String): AppResult<File> = result.also { downloads += url }
}
