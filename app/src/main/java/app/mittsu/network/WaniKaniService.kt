package app.mittsu.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * Read endpoints of WaniKani API v2. Collection calls take a full or relative URL so that
 * `pages.next_url` can be followed exactly as the API returns it.
 * The only write endpoint so far is POST /reviews, used solely by [app.mittsu.reviews.ReviewSubmitter].
 * PUT /assignments/{id}/start (lessons) is added in phase 5.
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

    @POST("reviews")
    suspend fun createReview(@Body body: CreateReviewRequest): Response<CreateReviewResponse>

    @GET
    suspend fun srsSystems(@Url url: String): Response<CollectionDto<SrsSystemDto>>
}
