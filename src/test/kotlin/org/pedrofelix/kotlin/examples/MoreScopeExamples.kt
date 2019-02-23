package org.pedrofelix.kotlin.examples

import kotlinx.coroutines.*
import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

private val log = LoggerFactory.getLogger(MoreScopeExamples::class.java)

class MoreScopeExamples {

    @Test
    fun example0() {
        val res = Array(4 + 2) { Result() }
        runBlocking {
            log.info("[1] start")

            launchEx(2) {
                completeAfter(300, 2, res)
            }

            launchEx(3) {
                launchEx(4) {
                    completeAfter(1000, 4, res)
                }
                launchEx(5) {
                    completeAfter(200, 5, res)
                }
                log.info("after launching [4] and [5]")
                completeAfter(500, 3, res)
            }
            log.info("[1] end")
        }
        log.info("complete")
    }

    @Test
    // [5] completes and then [2] throws ...
    fun example1() {
        val res = Array(4 + 2) { Result() }
        runBlocking {
            log.info("[1] start")

            launchEx(2) {
                throwAfter(300, 2, res)
            }

            launchEx(3) {
                launchEx(4) {
                    completeAfter(1000, 4, res)
                }
                launchEx(5) {
                    completeAfter(200, 5, res)
                }
                log.info("after launching [4] and [5]")
                completeAfter(500, 3, res)
            }
            log.info("[1] end")
        }
        log.info("complete")
    }

    @Test
    // [5] throws
    fun example2() {
        val res = Array(4 + 2) { Result() }
        runBlocking {
            log.info("[1] start")

            launchEx(2) {
                completeAfter(300, 2, res)
            }

            launchEx(3) {
                launchEx(4) {
                    completeAfter(1000, 4, res)
                }
                launchEx(5) {
                    throwAfter(200, 5, res)
                }
                completeAfter(500, 3, res)
            }
            log.info("after launching [4] and [5]")
            log.info("[1] end")
        }
        log.info("complete")
    }

    @Test
    // with a coroutineScope
    fun example3() {
        val res = Array(4 + 2) { Result() }
        runBlocking {
            log.info("[1] start")

            launchEx(2) {
                completeAfter(300, 2, res)
            }

            launchEx(3) {
                coroutineScope {
                    launchEx(4) {
                        completeAfter(1000, 4, res)
                    }
                    launchEx(5) {
                        completeAfter(200, 5, res)
                    }
                }
                log.info("after coroutineScope with [4] and [5]")
                completeAfter(500, 3, res)
            }
            log.info("after launching [4] and [5]")
            log.info("[1] end")
        }
        log.info("complete")
    }

    @Test
    // with a coroutineScope and a failure
    fun example4() {
        val res = Array(4 + 2) { Result() }
        runBlocking {
            log.info("[1] start")

            launchEx(2) {
                completeAfter(300, 2, res)
            }

            launchEx(3) {
                coroutineScope {
                    launchEx(4) {
                        completeAfter(1000, 4, res)
                    }
                    launchEx(5) {
                        throwAfter(200, 5, res)
                    }
                }
                log.info("after coroutineScope with [4] and [5]")
                completeAfter(500, 3, res)
            }
            log.info("after launching [4] and [5]")
            log.info("[1] end")
        }
        log.info("complete")
    }

    @Test
    // with a supervisorScope and a failure
    fun examplet() {
        val res = Array(4 + 2) { Result() }
        runBlocking {
            log.info("[1] start")

            launchEx(2) {
                completeAfter(300, 2, res)
            }

            launchEx(3) {
                supervisorScope {
                    launchEx(4) {
                        completeAfter(1000, 4, res)
                    }
                    launchEx(5) {
                        throwAfter(200, 5, res)
                    }
                }
                log.info("after supervisorScope with [4] and [5]")
                completeAfter(500, 3, res)
            }
            log.info("after launching [4] and [5]")
            log.info("[1] end")
        }
        log.info("complete")
    }

    fun CoroutineScope.launchEx(ix: Int, block: suspend CoroutineScope.() -> Unit) = launch(block=block).apply {
        this.invokeOnCompletion { cause ->
            log.info("[{}] completed with '{}'", ix, cause?.message ?: "success")
        }
    }

    suspend fun completeAfter(ms: Long, ix: Int, res: Array<Result>) {
        try {
            delay(ms)
        } catch (ex: CancellationException) {
            log.warn("[{}] delay ended with CancellationException '{}'", ix, ex.message)
            res[ix].completeWithCancellation()
            return
        }
        log.info("[{}] completing", ix)
        res[ix].complete()
    }

    suspend fun throwAfter(ms: Long, ix: Int, res: Array<Result>) {
        try {
            delay(ms)
        } catch (ex: CancellationException) {
            log.warn("[{}] delay ended with CancellationException '{}'", ix, ex.message)
            res[ix].completeWithCancellation()
            return
        }
        log.info("[{}] throwing", ix)
        res[ix].completeWithError()
        throw Exception("coroutine ${ix} ending with error")
    }

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
}