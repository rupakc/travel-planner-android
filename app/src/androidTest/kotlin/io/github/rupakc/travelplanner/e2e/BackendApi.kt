package io.github.rupakc.travelplanner.e2e

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Direct calls to the E2E backend, used to arrange state and to check what the app stored. Test-only. */
class BackendApi(private val baseUrl: String = E2eConfig.baseUrl) {
    private val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    data class Response(val code: Int, val body: String) {
        fun json(): JsonElement = Json.parseToJsonElement(body)
    }

    fun call(method: String, path: String, token: String? = null, body: JsonElement? = null): Response {
        val request =
            Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .method(method, body?.toString()?.toRequestBody(jsonType) ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(jsonType))
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .build()
        client.newCall(request).execute().use { return Response(it.code, it.body.string()) }
    }

    fun login(username: String, password: String): String {
        val r =
            call(
                "POST",
                "/api/auth/login",
                body =
                    buildJsonObject {
                        put("username", username)
                        put("password", password)
                    },
            )
        check(r.code == 200) { "login $username -> ${r.code} ${r.body}" }
        return r.json().jsonObject.getValue("access_token").jsonPrimitive.content
    }

    fun adminToken(): String = login(E2eConfig.ADMIN, E2eConfig.adminPassword)

    /** Creates the second user when missing and clears its forced password change (idempotent). */
    fun ensureSecondUser() {
        val admin = adminToken()
        call(
            "POST",
            "/api/admin/users",
            admin,
            buildJsonObject {
                put("username", E2eConfig.SECOND_USER)
                put("password", E2eConfig.SECOND_PASSWORD)
            },
        )
        val r =
            call(
                "POST",
                "/api/auth/login",
                body =
                    buildJsonObject {
                        put("username", E2eConfig.SECOND_USER)
                        put("password", E2eConfig.SECOND_PASSWORD)
                    },
            )
        check(r.code == 200) { "second user login -> ${r.code}" }
        val token = r.json().jsonObject.getValue("access_token").jsonPrimitive.content
        call(
            "POST",
            "/api/auth/change-password",
            token,
            buildJsonObject {
                put("current_password", E2eConfig.SECOND_PASSWORD)
                put("new_password", E2eConfig.SECOND_PASSWORD)
            },
        )
    }

    fun preferences(token: String): JsonObject = call("GET", "/api/preferences", token).json().jsonObject

    /** Resets the account's preferences to the server defaults so a run never depends on a previous one. */
    fun resetPreferences(token: String) {
        val r = call("PUT", "/api/preferences", token, buildJsonObject { })
        check(r.code == 200) { "reset preferences -> ${r.code} ${r.body}" }
    }

    fun plans(token: String): JsonArray {
        val body = call("GET", "/api/plans", token).json()
        return (body as? JsonArray) ?: (body.jsonObject["plans"] as JsonArray)
    }

    /** Creates a plan the way the web does (`POST /api/plans`); returns its id. */
    fun createPlan(token: String, name: String, searchData: JsonObject, selections: JsonObject = buildJsonObject { }): String {
        val r =
            call(
                "POST",
                "/api/plans",
                token,
                buildJsonObject {
                    put("name", name)
                    put("search_data", searchData)
                    put("selections", selections)
                },
            )
        check(r.code == 200) { "create plan -> ${r.code} ${r.body}" }
        return r.json().jsonObject.getValue("id").jsonPrimitive.content
    }

    fun lisbonSearchData(): JsonObject =
        buildJsonObject {
            put("origin", "JFK")
            put("destination", "LIS")
            put("departure_date", "2026-12-01")
            put("return_date", "2026-12-04")
            put("nationality", "American")
            put("num_travelers", 2)
            put("adults", 2)
        }

    fun plan(token: String, id: String): JsonObject = call("GET", "/api/plans/$id", token).json().jsonObject

    fun deletePlan(token: String, id: String) {
        call("DELETE", "/api/plans/$id", token)
    }

    fun parse(text: String): JsonElement = json.parseToJsonElement(text)
}
