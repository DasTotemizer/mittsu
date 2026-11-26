package app.mittsu.network

import kotlinx.coroutines.delay
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit

/** Failures callers can act on. Messages never contain the token. */
sealed class WaniKaniError(message: String) : Exception(message) {
    /** 401: the token is wrong, revoked or expired. */
    class Unauthorized : WaniKaniError("WaniKani did not accept this API token.")
    /** 403: the token lacks a permission this action needs. */
    class Forbidden : WaniKaniError("This API token does not have permission for that action.")
    class RateLimited : WaniKaniError("WaniKani is rate limiting requests. Try again in a minute.")
    class Network(cause: Throwable? = null) : WaniKaniError("Could not reach WaniKani. Check your connection.") {
        init { if (cause != null) initCause(cause) }
    }
    class Server(val code: Int) : WaniKaniError("WaniKani returned an error ($code).")
    class InvalidResponse(detail: String) : WaniKaniError("Unexpected response from WaniKani: $detail")
}

/** Adds the revision header to every call and the bearer token unless the call sets its own. */
class WaniKaniHeadersInterceptor(private val token: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val builder = original.newBuilder().header(WaniKaniConfig.REVISION_HEADER, WaniKaniConfig.REVISION)
        if (original.header("Authorization") == null) {
            token()?.let { builder.header("Authorization", bearer(it)) }
        }
        return chain.proceed(builder.build())
    }

    companion object {
        fun bearer(token: String) = "Bearer $token"
    }
}

/**
 * Thin wrapper over [WaniKaniService] that adds: error mapping, rate-limit handling
 * (waits for `RateLimit-Reset`), limited retries on server errors, and pagination.
 */
class WaniKaniClient(
    val service: WaniKaniService,
    private val clock: Clock = Clock.systemUTC(),
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val maxRateLimitWaits: Int = 3,
    private val maxServerRetries: Int = 2,
) {
    suspend fun <T> call(block: suspend () -> Response<T>): T {
        var rateLimitWaits = 0
        var serverRetries = 0
        while (true) {
            val response = try {
                block()
            } catch (e: IOException) {
                throw WaniKaniError.Network(e)
            } catch (e: kotlinx.serialization.SerializationException) {
                throw WaniKaniError.InvalidResponse("could not parse JSON")
            }
            when {
                response.isSuccessful -> return response.body()
                    ?: throw WaniKaniError.InvalidResponse("empty body")
                response.code() == 401 -> throw WaniKaniError.Unauthorized()
                response.code() == 403 -> throw WaniKaniError.Forbidden()
                response.code() == 429 -> {
                    if (rateLimitWaits++ >= maxRateLimitWaits) throw WaniKaniError.RateLimited()
                    sleep(rateLimitWaitMillis(response.headers()["RateLimit-Reset"]))
                }
                response.code() >= 500 -> {
                    if (serverRetries++ >= maxServerRetries) throw WaniKaniError.Server(response.code())
                    sleep(1_000L * (1 shl serverRetries))
                }
                else -> throw WaniKaniError.Server(response.code())
            }
        }
    }

    /** Waits until the reset time WaniKani gives (epoch seconds), between 1 s and 65 s. */
    internal fun rateLimitWaitMillis(resetHeader: String?): Long {
        val reset = resetHeader?.toLongOrNull() ?: return DEFAULT_RATE_LIMIT_WAIT_MS
        val wait = reset * 1000 - clock.millis() + 500
        return wait.coerceIn(1_000L, 65_000L)
    }

    /** Collects every page of a collection, following `pages.next_url`. */
    suspend fun <T> fetchAll(
        firstUrl: String,
        fetch: suspend (String) -> Response<CollectionDto<T>>,
    ): PagedResult<T> {
        val items = mutableListOf<ResourceDto<T>>()
        var url: String? = firstUrl
        var updatedAt: String? = null
        var pages = 0
        while (url != null) {
            val page = call { fetch(url!!) }
            if (page.objectType != "collection") throw WaniKaniError.InvalidResponse("expected a collection")
            items += page.data
            if (updatedAt == null) updatedAt = page.dataUpdatedAt
            url = page.pages?.nextUrl
            if (++pages > MAX_PAGES) throw WaniKaniError.InvalidResponse("too many pages")
        }
        return PagedResult(items, updatedAt)
    }

    data class PagedResult<T>(val items: List<ResourceDto<T>>, val dataUpdatedAt: String?)

    companion object {
        private const val DEFAULT_RATE_LIMIT_WAIT_MS = 10_000L
        private const val MAX_PAGES = 500

        fun create(
            baseUrl: String = WaniKaniConfig.BASE_URL,
            token: () -> String?,
        ): WaniKaniClient {
            val okHttp = OkHttpClient.Builder()
                .addInterceptor(WaniKaniHeadersInterceptor(token))
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            // No HTTP logging interceptor on purpose: it would print the Authorization header.
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttp)
                .addConverterFactory(WaniKaniJson.asConverterFactory("application/json".toMediaType()))
                .build()
            return WaniKaniClient(retrofit.create(WaniKaniService::class.java))
        }
    }
}
