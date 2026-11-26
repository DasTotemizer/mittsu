package app.mittsu.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * Read endpoints of WaniKani API v2. Collection calls take a full or relative URL so that
 * `pages.next_url` can be followed exactly as the API returns it.
 * Write endpoints (POST /reviews, PUT /assignments/{id}/start) are added in phases 4 and 5.
 */
interface WaniKaniService {
    @GET("user")
    suspend fun user(): Response<ResourceDto<UserDto>>

    /** Used only to validate a token before it is saved. */
    @GET("user")
    suspend fun userWithToken(@Header("Authorization") authorization: String): Response<ResourceDto<UserDto>>

    @GET
    suspend fun subjects(@Url url: String): Response<CollectionDto<SubjectDto>>

    @GET
    suspend fun assignments(@Url url: String): Response<CollectionDto<AssignmentDto>>

    @GET
    suspend fun reviewStatistics(@Url url: String): Response<CollectionDto<ReviewStatisticDto>>

    @GET
    suspend fun srsSystems(@Url url: String): Response<CollectionDto<SrsSystemDto>>
}
