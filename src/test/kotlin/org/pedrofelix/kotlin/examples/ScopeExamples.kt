package org.pedrofelix.experiments

import kotlinx.coroutines.*
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.LoggerFactory
import java.lang.AssertionError
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val log = LoggerFactory.getLogger(ScopeExamples::class.java)

class ScopeExamples {

    @Test
    fun runBlocking_doesnt_throw_because_inner_coroutines_are_on_GlobalScope() {
        var called = AtomicBoolean()
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            log.info("My exception handler: Exception {} on thread {}", ex.message, th.name)
            called.set(true)
        }

        val cr1 = Result()
        val cr2 = Result()

        log.info("start")
        runBlocking {
            GlobalScope.launch {
                throwAfter(500, cr1)
            }
            GlobalScope.launch {
                completeAfter(1000, cr2)
            }
        }
        // runBlocking doesn't wait on inner coroutines
        // (they are not connected because they were started in the GlobalScope)
        assertFalse(called.get())
        assertFalse(cr1.isCompleted())
        assertFalse(cr2.isCompleted())
        log.info("end")

        // waiting so the GlobalScope.launch have time to end
        Thread.sleep(1500);
        // however the default handler is called (look at the standard output)
        assertTrue(called.get())
        assertTrue(cr1.isError())
        assertTrue(cr2.isSuccess())
    }

    @Test
    fun runBlocking_doesnt_throw_and_there_is_not_default_handling_for_async() {
        var called = AtomicBoolean()
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            log.info("My exception handler: Exception {} on thread {}", ex.message, th.name)
            called.set(true)
        }
        val cr1 = Result()
        val cr2 = Result()
        log.info("start")
        runBlocking {
            GlobalScope.async {
                throwAfter(500, cr1)
            }
            GlobalScope.async {
                completeAfter(1000, cr2)
            }
        }
        // runBlocking doesn't wait on inner coroutines
        // (they are not connected because they were started in the GlobalScope)
        assertFalse(cr1.isCompleted())
        assertFalse(cr2.isCompleted())
        log.info("end")
        Thread.sleep(1500);
        // and the default handler is not called
        assertFalse(called.get())
        assertTrue(cr1.isError())
        assertTrue(cr2.isSuccess())
    }

    @Test
    fun runBlocking_throws_due_to_child_coroutine_exception() {
        val cr1 = Result()
        val cr2 = Result()
        log.info("start")
        assertExceptionHappens {
            runBlocking {
                launch {
                    throwAfter(500, cr1)
                }
                launch {
                    completeAfter(1000, cr2)
                }
                log.info("after launches")
            }
        }
        assertTrue(cr1.isCompleted())
        assertTrue(cr2.isCompleted())
        assertTrue(cr1.isError())
        assertTrue(cr2.isCancelled())
        log.info("end")
    }

    @Test
    fun runBlocking_throws_due_to_child_coroutine_exception_async() {
        val cr1 = Result()
        val cr2 = Result()
        log.info("start")
        assertExceptionHappens {
            runBlocking {
                async {
                    throwAfter(500, cr1)
                }
                async {
                    completeAfter(1000, cr2)
                }
                log.info("after asyncs")
            }
        }
        assertTrue(cr1.isCompleted())
        assertTrue(cr2.isCompleted())
        assertTrue(cr1.isError())
        assertTrue(cr2.isCancelled())
        log.info("end")
    }

    @Test
    fun nested_coroutines() {
        var called = AtomicBoolean()
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            log.info("My exception handler: Exception {} on thread {}", ex.message, th.name)
            called.set(true)
        }
        val cr1 = Result()
        val cr2 = Result()
        log.info("start")
        // No exception because the `GlobalScope` filters out the errors from the inner async
        runBlocking {
            GlobalScope.launch {
                async {
                    throwAfter(500, cr1)
                }
                async {
                    completeAfter(1000, cr2)
                }
            }
        }
        log.info("end")
        assertFalse(cr1.isCompleted())
        assertFalse(cr2.isCompleted())
        // waiting for background coroutines to end
        Thread.sleep(2000)
        assertTrue(called.get())
        assertTrue(cr1.isError())
        assertTrue(cr2.isCancelled())
    }

    @Test
    fun nested_coroutines2() {
        val called = AtomicBoolean()
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            log.info("My exception handler: Exception {} on thread {}", ex.message, th.name)
            called.set(true)
        }
        val cr1 = Result()
        val cr2 = Result()
        log.info("start")
        runBlocking {
            val job = GlobalScope.launch {
                async {
                    throwAfter(500, cr1)
                }
                async {
                    completeAfter(1000, cr2)
                }
            }
            assertFalse(cr1.isCompleted())
            assertFalse(cr2.isCompleted())
            // This will not throw, which seems odd. TODO need to check if it's a bug or not
            job.join()
            assertTrue(cr1.isCompleted())
            assertTrue(cr2.isCompleted())
            log.info("after join")
        }
        // They are completed because the `join` waits for it
        assertTrue(cr1.isCompleted())
        assertTrue(cr2.isCompleted())
        log.info("end")
        Thread.sleep(2000)
        assertTrue(cr1.isError())
        assertTrue(cr2.isCancelled())
    }

    @Test
    fun nested_coroutines3() {
        val called = AtomicBoolean()
        val cr1 = Result()
        val cr2 = Result()
        log.info("start")
        assertExceptionHappens {
            runBlocking {
                val job = launch {
                    async {
                        throwAfter(500, cr1)
                    }
                    async {
                        completeAfter(1000, cr2)
                    }
                }
                assertFalse(cr1.isCompleted())
                assertFalse(cr2.isCompleted())
                job.join()
                log.info("after join")
            }
        }
        assertTrue(cr1.isCompleted())
        assertTrue(cr2.isCompleted())
        log.info("end")
    }

    @Test
    fun nested_coroutines4() {
        val cr1 = Result()
        val cr2 = Result()
        val cr3 = Result()
        log.info("start")
        val start = System.currentTimeMillis()
        fun took() = System.currentTimeMillis() - start
        val someError = 500
        val res = runBlocking {
            launch {
                completeAfter(2000, cr1)
            }
            val d1 = async {
                completeAfter(500, cr2)
                1
            }
            val d2 = async {
                completeAfter(1000, cr3)
                2
            }
            log.info("before await")
            assertFalse(cr1.isCompleted())
            assertFalse(cr2.isCompleted())
            assertFalse(cr3.isCompleted())
            val sum = d1.await() + d2.await()
            assertFalse(cr1.isCompleted())
            assertTrue(cr2.isCompleted())
            assertTrue(cr3.isCompleted())
            log.info("after await, before return")
            assertTrue(took() < 1000 + someError)
            sum
        }
        assertTrue(cr1.isCompleted())
        assertTrue(cr2.isCompleted())
        assertTrue(cr3.isCompleted())
        assertTrue(took() >= 2000)
        log.info("end")
    }

    @Test
    fun nested_coroutines5() {
        val crs = Array(5) { Result() }
        log.info("start")
        val start = System.currentTimeMillis()
        fun took() = System.currentTimeMillis() - start
        runBlocking {
            launch {
                launch {
                    completeAfter(2500, crs[1])
                }
                completeAfter(2000, crs[0])
            }
            GlobalScope.launch {
                completeAfter(3000, crs[2])
            }
            val d1 = async {
                completeAfter(500, crs[3])
                1
            }
            val d2 = async {
                completeAfter(1000, crs[4])
                2
            }
            log.info("before await")
            assertFalse(crs.any { it.isCompleted() })
            val sum = d1.await() + d2.await()
            log.info("after await, before return")
            sum
        }

        // Notice how the outer runBlocking will wait for the
        // completeAfter(2500) which is two levels deep
        assertTrue(took() >= 2500)
        assertTrue(crs.all { it.isCompleted() })
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

    class Result {
        private val state = AtomicInteger(0)
        fun complete() = state.set(1)
        fun completeWithCancellation() = state.set(2)
        fun completeWithError() = state.set(3)
        fun isCompleted() = state.get() != 0
        fun isSuccess() = state.get() == 1
        fun isCancelled() = state.get() == 2
        fun isError() = state.get() == 3
    }

    suspend fun throwAfter(ms: Long, res: Result) {
        try {
            delay(ms)
        } catch (ex: CancellationException) {
            log.warn("cancelled")
            res.completeWithCancellation()
            return
        }
        log.info("throwing")
        res.completeWithError()
        throw Exception("coroutine 2 ending with error")
    }

    suspend fun completeAfter(ms: Long, res: Result) {
        try {
            delay(ms)
        } catch (ex: CancellationException) {
            log.warn("cancelled")
            res.completeWithCancellation()
            return
        }
        log.info("completing")
        res.complete()
    }

}