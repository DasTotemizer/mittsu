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

    val baseUrl: String get() = server.url("/v2/").toString()

    private fun load(name: String): String =
        javaClass.getResource("/wanikani/$name.json")!!.readText().replace("__BASE__", baseUrl)

    fun client(token: () -> String?) = WaniKaniClient(
        service = WaniKaniClient.create(baseUrl, token).service,
        sleep = { },
    )

    fun shutdown() = server.shutdown()
}
