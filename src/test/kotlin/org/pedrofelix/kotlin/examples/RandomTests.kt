package org.pedrofelix.kotlin.examples

import kotlinx.coroutines.*
import org.junit.Test
import org.pedrofelix.experiments.ScopeExamples
import org.slf4j.LoggerFactory
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

private val log = LoggerFactory.getLogger(RandomTests::class.java)

class RandomTests {

    private suspend fun doSomethingWith(i: Int) {
        log.info("ctx {}", coroutineContext)
    }

    @Test
    fun first() {
        val items = listOf(1,2,3,4)
        runBlocking {
            withContext(Dispatchers.IO) {
                log.info("ctx {}", coroutineContext)
                items.forEach { launch { doSomethingWith(it) } }
            }
        }
    }
}
