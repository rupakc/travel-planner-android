package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.SharesRepository
import io.github.rupakc.travelplanner.core.model.share.CreateShareRequest
import io.github.rupakc.travelplanner.core.model.share.CreatedShare
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.model.share.ShareOptions
import io.github.rupakc.travelplanner.core.model.share.SharesList
import io.github.rupakc.travelplanner.core.network.api.SharesApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharesRepositoryImpl
    @Inject
    constructor(
        private val api: SharesApi,
        private val language: AppLanguage,
    ) : SharesRepository {
        override suspend fun create(kind: ShareKind, planId: Long?, options: ShareOptions, sessionId: String?): AppResult<CreatedShare> {
            val wire = kind.wire ?: return AppResult.Failure(io.github.rupakc.travelplanner.core.common.AppError.Unknown(null))
            // plan_id is ignored by the server for savings; it is not sent so the body is exactly the contract's.
            val body = CreateShareRequest(wire, planId.takeIf { kind.needsPlan }, options, language.current(), sessionId.takeIf { kind.needsSession })
            return apiCall { api.create(body) }
        }

        override suspend fun list(): AppResult<SharesList> = apiCall { api.list() }

        override suspend fun revoke(token: String): AppResult<Unit> =
            apiCall {
                val response = api.revoke(token)
                if (!response.isSuccessful) throw retrofit2.HttpException(response)
            }
    }
