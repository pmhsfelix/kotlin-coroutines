package pmhsfelix.kotlin.coroutines

import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.nio.client.HttpAsyncClient
import java.lang.Exception
import java.util.concurrent.TimeoutException
import kotlin.coroutines.experimental.suspendCoroutine

suspend fun HttpAsyncClient.execute(request: HttpUriRequest): HttpResponse =
        suspendCoroutine {continuation ->
            this.execute(request, object : FutureCallback<HttpResponse> {
                override fun cancelled() {
                    continuation.resumeWithException(TimeoutException())
                }

                override fun completed(result: HttpResponse?) {
                    continuation.resume(result!!)
                }

                override fun failed(ex: Exception?) {
                    continuation.resumeWithException(ex!!)
                }
            })
        }

suspend fun HttpAsyncClient.get(url: String, token: String) =
        this.execute(HttpGet(url).apply {
            addHeader("Authorization", "Bearer $token")
        })

