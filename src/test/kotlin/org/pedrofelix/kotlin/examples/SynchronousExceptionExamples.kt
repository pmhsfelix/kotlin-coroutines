package org.pedrofelix.kotlin.examples

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SynchronousExceptionExamples::class.java)

class SynchronousExceptionExamples {
    @Test
    fun example() {
        log.info("before launch")
        val job = GlobalScope.launch(Dispatchers.Unconfined) {
            log.info("before throw")
            throw Exception("an-error")
        }
        log.info("after launch")
        Thread.sleep(1000)
        assertTrue(job.isCancelled)
    }
}
