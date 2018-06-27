package pmhsfelix.kotlin.coroutines

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.asynchttpclient.BoundRequestBuilder
import org.asynchttpclient.DefaultAsyncHttpClient
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("repos")
private val mapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
private val token = "1088a73d4016f27cbc606400f351849c3de197eb"
fun BoundRequestBuilder.withToken() = this.addHeader("Authorization", "Token $token")


suspend fun showTagsForReposInOrgs(orgUrl: String) {
    HttpAsyncClients.createDefault().use { client ->
        client.start()
        log.info("Getting repo list from $orgUrl")
        val resp = client.get(orgUrl, token)
        val repoList = mapper.readValue<List<Repo>>(resp.entity.content)
        repoList.map {
                log.info("Getting tags from ${it.tags_url}")
                val resp = client.get(it.tags_url, token)
                mapper.readValue<List<Tag>>(resp.entity.content).forEach {
                    log.info("tag ${it.name} found")
                }
        }
    }
}

suspend fun getTagsForReposInOrgs(orgUrl: String) =
    HttpAsyncClients.createDefault().use { client ->
        client.start()
        log.info("Getting repo list from $orgUrl")
        val resp = client.get(orgUrl, token)
        val repoList = mapper.readValue<List<Repo>>(resp.entity.content)
        repoList.map {
            log.info("Getting tags from ${it.tags_url}")
            val resp = client.get(it.tags_url, token)
            val name = it.name
            val tags = mapper.readValue<List<Tag>>(resp.entity.content).map {
                it.name
            }
            RepoAndTags(name, tags)
        }
    }

suspend fun showTagsForReposInOrgsUsingAsyncHttpClient(orgUrl: String) {
    DefaultAsyncHttpClient().use { client ->
        log.info("Getting repo list from $orgUrl")
        val resp = client.prepareGet(orgUrl).withToken()
                .execute().toCompletableFuture().await()
        val repoList = mapper.readValue<List<Repo>>(resp.responseBody)
        repoList.map {
            log.info("Getting tags from ${it.tags_url}")
            val resp = client.prepareGet(it.tags_url).withToken()
                    .execute().toCompletableFuture().await()
            mapper.readValue<List<Tag>>(resp.responseBody).forEach {
                log.info("tag ${it.name} found")
            }
        }
    }
}

private data class Repo(val name: String, val url: String, val tags_url: String)
private data class Tag(val name: String)
public data class RepoAndTags(val name: String, val tags: List<String>)

