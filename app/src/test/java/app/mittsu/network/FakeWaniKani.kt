package app.mittsu.network

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList

/** A scripted stand-in for api.wanikani.com, serving fixture JSON from test resources. */
class FakeWaniKani {
    val server = MockWebServer()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    val validToken = "00000000-1111-2222-3333-444444444444"
    var assignmentsFixture = "assignments"
    var rateLimitNext = 0
    var serverErrorNext = 0
    var revokeToken = false
    /** (assignment id, body) of every PUT /assignments/{id}/start received, in order. */
    val lessonStarts = CopyOnWriteArrayList<Pair<Long, String>>()
    var lessonResponder: (assignmentId: Long, body: String) -> MockResponse = { id, _ -> assignmentStarted(id) }
    /** Bodies of every POST /reviews received, in order. */
    val postedReviews = CopyOnWriteArrayList<String>()
    /** How POST /reviews answers; by default it accepts the review like WaniKani does. */
    var reviewResponder: (assignmentId: Long, body: String) -> MockResponse = { id, _ -> reviewCreated(id) }

    fun start(): FakeWaniKani {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                if (rateLimitNext > 0) {
                    rateLimitNext--
                    return MockResponse().setResponseCode(429).addHeader("RateLimit-Reset", "0")
                }
                if (serverErrorNext > 0) {
                    serverErrorNext--
                    return MockResponse().setResponseCode(503)
                }
                if (revokeToken || request.getHeader("Authorization") != "Bearer $validToken") {
                    return MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized","code":401}""")
                }
                val path = request.requestUrl!!.encodedPath.removePrefix("/v2/")
                if (request.method == "POST" && path == "reviews") {
                    val body = request.body.readUtf8()
                    postedReviews += body
                    val id = Regex("\"assignment_id\":(\\d+)").find(body)!!.groupValues[1].toLong()
                    return reviewResponder(id, body)
                }
                val start = Regex("assignments/(\\d+)/start").matchEntire(path)
                if (request.method == "PUT" && start != null) {
                    val id = start.groupValues[1].toLong()
                    val body = request.body.readUtf8()
                    lessonStarts += id to body
                    return lessonResponder(id, body)
                }
                if (request.method != "GET") return MockResponse().setResponseCode(405)
                val query = request.requestUrl!!.query
                val fixture = when (path) {
                    "user" -> "user"
                    "subjects" -> if (query?.contains("page_after_id") == true) "subjects_page2"
                        else if (query?.contains("updated_after") == true) "empty_collection" else "subjects_page1"
                    "assignments" -> if (query?.contains("updated_after") == true) "assignments_update" else assignmentsFixture
                    "review_statistics" -> if (query?.contains("updated_after") == true) "empty_collection" else "review_statistics"
                    "spaced_repetition_systems" -> "srs_systems"
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setResponseCode(200).setBody(load(fixture))
            }
        }
        server.start()
        return this
    }

    /** A 201 like WaniKani's, including the updated assignment moved to its next interval. */
    fun reviewCreated(assignmentId: Long, subjectId: Long = assignmentId % 1000, nextAvailableAt: String = "2026-12-24T00:00:00.000000Z") = MockResponse()
        .setResponseCode(201)
        .setBody(
            """{"id":${assignmentId + 9000},"object":"review","url":"x","data_updated_at":"2026-11-26T12:00:00.000000Z",
            "data":{"created_at":"2026-11-26T12:00:00.000000Z","assignment_id":$assignmentId,"subject_id":$subjectId,
            "spaced_repetition_system_id":1,"starting_srs_stage":4,"ending_srs_stage":5,"incorrect_meaning_answers":0,"incorrect_reading_answers":0},
            "resources_updated":{"assignment":{"id":$assignmentId,"object":"assignment","url":"x","data_updated_at":"x",
            "data":{"subject_id":$subjectId,"subject_type":"kanji","srs_stage":5,"unlocked_at":"2026-01-01T00:00:00.000000Z",
            "started_at":"2026-01-01T00:00:00.000000Z","passed_at":null,"burned_at":null,"available_at":"$nextAvailableAt","resurrected_at":null,"hidden":false}},
            "review_statistic":{"id":${assignmentId + 5000},"object":"review_statistic","url":"x","data_updated_at":"x",
            "data":{"subject_id":$subjectId,"subject_type":"kanji","meaning_correct":5,"meaning_incorrect":1,"reading_correct":4,"reading_incorrect":2,"percentage_correct":75,"hidden":false}}}}""",
        )

    fun assignmentStarted(assignmentId: Long, subjectId: Long = assignmentId - 1000) = MockResponse().setResponseCode(200).setBody(
        """{"id":$assignmentId,"object":"assignment","url":"x","data_updated_at":"2026-11-26T12:00:00.000000Z",
        "data":{"subject_id":$subjectId,"subject_type":"kanji","srs_stage":1,"unlocked_at":"2026-11-01T00:00:00.000000Z",
        "started_at":"2026-11-26T12:00:00.000000Z","passed_at":null,"burned_at":null,"available_at":"2026-11-26T16:00:00.000000Z","resurrected_at":null,"hidden":false}}""",
    )

    fun reviewRefused(message: String) = MockResponse().setResponseCode(422).setBody("""{"error":"$message","code":422}""")

    val baseUrl: String get() = server.url("/v2/").toString()

    private fun load(name: String): String =
        javaClass.getResource("/wanikani/$name.json")!!.readText().replace("__BASE__", baseUrl)

    fun client(token: () -> String?) = WaniKaniClient(
        service = WaniKaniClient.create(baseUrl, token).service,
        sleep = { },
    )

    fun shutdown() = server.shutdown()
}
