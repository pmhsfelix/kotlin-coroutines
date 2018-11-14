package org.pedrofelix.experiments

import kotlinx.coroutines.*
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.LoggerFactory
import java.lang.AssertionError
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger(ScopeExamples::class.java)

class ScopeExamples {

    @Test
    fun runBlocking_doesnt_throw_because_inner_coroutines_are_on_GlobalScope() {
        var called = AtomicBoolean()
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            log.info("My exception handler: Exception {} on thread {}", ex.message, th.name)
            called.set(true)
        }

        log.info("start")
        runBlocking {
            GlobalScope.launch {
                throwAfter(500)
            }
            GlobalScope.launch {
                completeAfter(1000)
            }
        }
        // runBlocking doesn't wait on inner coroutines
        // (they are not connected)
        log.info("end") //

        // waiting so the GlobalScope.launch have time to end
        Thread.sleep(1500);
        // however the default handler is called (look at the standard output)
        assertTrue(called.get())
    }

    @Test
    fun runBlocking_doesnt_throw_and_there_is_not_default_handling_for_async() {
        var called = AtomicBoolean()
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            log.info("My exception handler: Exception {} on thread {}", ex.message, th.name)
            called.set(true)
        }
        log.info("start")
        runBlocking {
            GlobalScope.async {
                throwAfter(500)
            }
            GlobalScope.async {
                completeAfter(1000)
            }
        }
        // runBlocking doesn't wait on inner coroutines
        // (they are not connected)
        log.info("end")
        Thread.sleep(1500);
        // and the default handler is not called
        assertFalse(called.get())
    }

    @Test
    fun runBlocking_throws_due_to_child_coroutine_exception() {
        log.info("start")
        assertExceptionHappens {
            runBlocking {
                launch {
                    throwAfter(500)
                }
                launch {
                    completeAfter(1000)
                }
                log.info("after launches")
            }
        }
        log.info("end")
    }

    @Test
    fun runBlocking_throws_due_to_child_coroutine_exception_async() {
        log.info("start")
        assertExceptionHappens {
            runBlocking {
                async {
                    throwAfter(500)
                }
                async {
                    completeAfter(1000)
                }
                log.info("after asyncs")
            }
        }
        log.info("end")
    }

    @Test
    fun nested_coroutines() {
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            log.info("My exception handler: Exception {} on thread {}", ex.message, th.name)
        }
        log.info("start")
        // No exception because the `GlobalScope` filters out the errors from the inner async
        runBlocking {
            GlobalScope.launch {
                async {
                    throwAfter(500)
                }
                async {
                    completeAfter(1000)
                }
            }
        }
        log.info("end")
        // waiting for background coroutines to end
        Thread.sleep(2000)
    }

    @Test
    fun nested_coroutines2() {
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            log.info("My exception handler: Exception {} on thread {}", ex.message, th.name)
        }
        log.info("start")
        runBlocking {
            val job = GlobalScope.launch {
                async {
                    throwAfter(500)
                }
                async {
                    completeAfter(1000)
                }
            }
            // This will not throw, which seems odd. TODO need to check if it's a bug or not
            job.join()
            log.info("after join")
        }
        log.info("end")
        Thread.sleep(2000)
    }

    @Test
    fun nested_coroutines3() {
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            log.info("My exception handler: Exception {} on thread {}", ex.message, th.name)
        }
        log.info("start")
        assertExceptionHappens {
            runBlocking {
                val job = launch {
                    async {
                        throwAfter(500)
                    }
                    async {
                        completeAfter(1000)
                    }
                }
                job.join()
                log.info("after join")
            }
        }
        log.info("end")
        Thread.sleep(2000)
    }

    @Test
    fun nested_coroutines4() {
        log.info("start")
        val start = System.currentTimeMillis()
        fun took() = System.currentTimeMillis() - start
        val someError = 500
        val res = runBlocking {
            launch {
                completeAfter(2000)
            }
            val d1 = async {
                completeAfter(500)
                1
            }
            val d2 = async {
                completeAfter(1000)
                2
            }
            log.info("before await")
            val sum = d1.await() + d2.await()
            log.info("after await, before return")
            assertTrue(took() < 1000 + someError)
            sum
        }
        assertTrue(took() >= 2000)
        log.info("end")
    }

    @Test
    fun nested_coroutines5() {
        log.info("start")
        val start = System.currentTimeMillis()
        fun took() = System.currentTimeMillis() - start
        runBlocking {
            launch {
                launch {
                    completeAfter(2500)
                }
                completeAfter(2000)
            }
            GlobalScope.launch {
                completeAfter(3000)
            }
            val d1 = async {
                completeAfter(500)
                1
            }
            val d2 = async {
                completeAfter(1000)
                2
            }
            log.info("before await")
            val sum = d1.await() + d2.await()
            log.info("after await, before return")
            sum
        }
        // Notice how the outer runBlocking will wait for the
        // completeAfter(2500) which is two levels deep
        assertTrue(took() >= 2500)
        log.info("end")
    }

    fun assertExceptionHappens(block: () -> Unit): Unit {
        try {
            block()
            Assert.fail("must thrown an exception")
        } catch (ex: AssertionError) {
            throw ex
        } catch (ex: Throwable) {
            log.info("expected exception '{}'", ex.message)
        }
    }

    // Just for demo purposes ...

    suspend fun throwAfter(ms: Long) {
        try {
            delay(ms)
        } catch (ex: CancellationException) {
            log.warn("cancelled")
            return
        }
        log.info("throwing")
        throw Exception("coroutine 2 ending with error")
    }

    suspend fun completeAfter(ms: Long) {
        try {
            delay(ms)
        } catch (ex: CancellationException) {
            log.warn("cancelled")
            return
        }
        log.info("completing")
    }
}