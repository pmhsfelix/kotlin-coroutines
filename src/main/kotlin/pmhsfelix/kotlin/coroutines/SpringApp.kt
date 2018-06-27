package pmhsfelix.kotlin.coroutines;

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.await
import kotlinx.coroutines.experimental.future.future
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.concurrent.FutureCallback
import org.asynchttpclient.DefaultAsyncHttpClient
import org.asynchttpclient.Response
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.springframework.web.context.request.async.DeferredResult
import java.lang.Exception
import java.util.concurrent.TimeoutException
import kotlin.coroutines.experimental.*


@SpringBootApplication
class SpringApp {
    @Bean
    fun config() = object : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            registry.addInterceptor(object : HandlerInterceptor {
                override fun preHandle(request: HttpServletRequest,
                                       response: HttpServletResponse,
                                       handler: Any): Boolean {
                    val start = System.nanoTime()
                    request.setAttribute("ts", start)
                    return true
                }

                override fun postHandle(request: HttpServletRequest,
                                        response: HttpServletResponse,
                                        handler: Any, modelAndView: ModelAndView?) {
                    val end = System.nanoTime()
                    var start = request.getAttribute("ts") as Long
                    log.info("Request to ${request.requestURI} took ${(end - start) / 1_000_000F} ms")
                }
            })
        }
    }
}

fun main(args: Array<String>) {
    runApplication<SpringApp>(*args)
}

private val log = LoggerFactory.getLogger(ExampleController::class.java)

@RestController
@RequestMapping("/example")
class ExampleController {

    val client = DefaultAsyncHttpClient()
    val mapper = ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun get(url: String): CompletableFuture<Response> = client.prepareGet(url).execute().toCompletableFuture()
    fun getGitHub(url: String) = client.prepareGet(url)
            .addHeader("Authorization", "Bearer 1088a73d4016f27cbc606400f351849c3de197eb")
            .execute()
            .toCompletableFuture()

    val apacheClient = HttpAsyncClients.createDefault().apply { start() }
    suspend fun apacheGetGitHub(url: String): HttpResponse {
        val get = HttpGet(url)
        get.addHeader("Authorization", "Bearer 1088a73d4016f27cbc606400f351849c3de197eb")
        return suspendCoroutine { cont ->
            apacheClient.execute(get, object : FutureCallback<HttpResponse> {
                override fun cancelled() {
                   cont.resumeWithException(TimeoutException())
                }

                override fun completed(result: HttpResponse?) {
                   cont.resume(result!!)
                }

                override fun failed(ex: Exception?) {
                    cont.resumeWithException(ex!!)
                }
            })
        }
    }


    val counter = AtomicInteger()
    fun getId() = counter.getAndIncrement()

    @GetMapping("/1")
    fun get1(): CompletableFuture<String?> {
        val cf = CompletableFuture<String?>()
        get("http://httpbin.org/get")
                .thenAccept { resp ->
                    val body = resp.responseBody
                    client.close()
                    cf.complete(body)
                }
        return cf
    }

    @GetMapping("/2")
    fun get2(): CompletableFuture<String?> =
            future {
                val resp = get("http://httpbin.org/get").await()
                resp.responseBody
            }

    @GetMapping("/3")
    fun get3(): CompletableFuture<List<RepoAndTagsModel>> =
            future {
                val id = getId()
                log.info("[$id] Getting repo list")
                val resp = getGitHub("https://api.github.com/users/ktorio/repos").await()
                val repoList = mapper.readValue<List<RepoModel>>(resp.responseBody)
                repoList.map {
                    log.info("[$id] Getting ${it.tags_url}")
                    val resp = getGitHub(it.tags_url).await()
                    val tags = mapper.readValue<List<TagModel>>(resp.responseBody)
                    RepoAndTagsModel(it.name, tags.map { it.name })
                }
            }

    @GetMapping("/4")
    fun get4(): CompletableFuture<List<RepoAndTagsModel>> =
            future(Unconfined) {
                val id = getId()
                log.info("[$id] Getting repo list")
                val resp = getGitHub("https://api.github.com/users/ktorio/repos").await()
                val repoList = mapper.readValue<List<RepoModel>>(resp.responseBody)
                log.info("[$id] Getting tags for all repos")
                val reqs = repoList.map { TagAsyncResponse(it.name, getGitHub(it.tags_url)) }
                reqs.map {
                    log.info("[$id] Waiting for response")
                    val resp = it.response.await()
                    val tags = mapper.readValue<List<TagModel>>(resp.responseBody)
                    RepoAndTagsModel(it.repoName, tags.map { it.name })
                }
            }


    @GetMapping("/5")
    fun get5(): CompletableFuture<List<RepoAndTagsModel>> =
            future(Unconfined) {
                val id = getId()
                log.info("[$id] Getting repo list")
                val resp = apacheGetGitHub("https://api.github.com/users/ktorio/repos")
                val repoList = mapper.readValue<List<RepoModel>>(resp.entity.content)
                log.info("[$id] Getting tags for all repos")
                val reqs = repoList.map { TagAsyncResponse(it.name, future(Unconfined) {apacheGetGitHub(it.tags_url)}) }
                reqs.map {
                    log.info("[$id] Waiting for response")
                    val resp = it.response.await()
                    val tags = mapper.readValue<List<TagModel>>(resp.entity.content)
                    RepoAndTagsModel(it.repoName, tags.map { it.name })
                }
            }

    fun <T> deferred(block: suspend () -> T): DeferredResult<T> {
        val result = DeferredResult<T>()
        block.startCoroutine(object : Continuation<T> {
            override fun resume(value: T) {
                result.setResult(value)
            }

            override fun resumeWithException(exception: Throwable) {
                result.setErrorResult(exception)
            }

            override val context: CoroutineContext
                get() = EmptyCoroutineContext
        })
        return result
    }

    @GetMapping("/tags")
    fun get6(): DeferredResult<List<RepoAndTags>> =
            deferred {
                getTagsForReposInOrgs("https://api.github.com/users/ktorio/repos")
            }
}


private data class TagAsyncResponse<T>(val repoName: String, val response: CompletableFuture<T>)
private data class RepoModel(val name: String, val url: String, val tags_url: String)
private data class TagModel(val name: String)
data class RepoAndTagsModel(val name: String, val tags: List<String>)
