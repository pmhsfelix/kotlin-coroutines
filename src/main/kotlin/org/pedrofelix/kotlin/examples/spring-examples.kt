package org.pedrofelix.kotlin.examples

import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.AsyncRequestTimeoutException
import org.springframework.web.context.request.async.DeferredResult
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val log = LoggerFactory.getLogger(DemoApplication::class.java)

// Just a very simple example distacher
private val exampleDispatcher = Executors.newSingleThreadExecutor { r ->
    Thread(r).apply { name = "the-example-thread" }
}.asCoroutineDispatcher()

// The startup class
@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

// The example Controller
@RestController
class TheController {

    // Simple example of using Coroutines on a Spring MVC controller
    // GlobalScope is not a good idea and will be handled in other handlers
    // However we need to start somewhere...
    // Using an explicit time to make it easy to demo
    @GetMapping("/example")
    fun getExample() = DeferredResult<Int>(5000).apply {
        log.info("controller handler starting")
        GlobalScope.launch {
            try {
                setResult(example7(1))
            } catch (ex: Throwable) {
                setErrorResult(ex)
            }
        }
        log.info("controller handler ending")
    }

    // Example using a small timeout so the problems of using GlobalScope
    // are illustrated
    @GetMapping("/example-with-small-timeout")
    fun getExampleWithSmallTimeout() = DeferredResult<Int>(1000).apply {
        log.info("controller handler starting")
        GlobalScope.launch {
            try {
                setResult(example7(1))
            } catch (ex: Throwable) {
                setErrorResult(ex)
            }
        }
        log.info("controller handler ending")
    }

    // And now using a proper scope to cancel the running coroutines on timeout
    @GetMapping("/example-with-small-timeout-and-scope")
    fun getExampleWithSmallTimeoutAndScope() = DeferredResultScope<Int>(1000).apply {
        log.info("controller handler starting")
        this.launch {
            try {
                setResult(example7(1))
            } catch (ex: Throwable) {
                log.info("catched '{}'", ex.message)
                setErrorResult(ex)
            }
        }
        log.info("controller handler ending")
    }

    // Using the asyncHandler function to make this pretty
    @GetMapping("/example-async-handler")
    fun getExampleWithAsyncHandler(input: Int) = asyncHandler {
        var acc = input
        for (i in 0..2) {
            log.info("before asyncOperationReturningFuture")
            val result = asyncOperationReturningFuture(acc).await()
            log.info("after asyncOperationReturningFuture")
            acc = doSomethingWith(result)
        }
        acc
    }

    // Using `withContext` to change the used dispatcher
    @GetMapping("/example-with-dispatcher")
    fun getExampleWithDispatcher() = asyncHandler {
        withContext(exampleDispatcher) {
            example7(1)
        }
    }

    // Passing the context directly on the helper `asyncHandler`
    @GetMapping("/example-with-dispatcher2")
    fun getExampleWithDispatcher2() = asyncHandler(context = exampleDispatcher) {
        example7(1)
    }

    @GetMapping("/example1")
    fun get1() = DeferredResult<String>(5000).apply {
        log.info("starting controller handler")
        GlobalScope.launch {
            try {
                val s1 = oper1()
                val s2 = oper2()
                setResult("($s1) and ($s2)")
            } catch (ex: Throwable) {
                log.warn("catching '{}'", ex.message)
                setErrorResult(ex)
            }
        }
        log.info("returning from controller handler")
    }

    @GetMapping("/example2")
    fun get2() = DeferredResult<String>(1000).apply {
        log.info("starting controller handler")
        GlobalScope.launch {
            try {
                val s1 = oper1()
                val s2 = oper2()
                setResult("($s1) and ($s2)")
            } catch (ex: Throwable) {
                log.warn("catching '{}'", ex.message)
                setErrorResult(ex)
            }
        }
        log.info("returning from controller handler")
    }

    @GetMapping("/example3")
    fun get3() = DeferredResultScope<String>(1000).apply {
        log.info("starting controller handler")
        this.launch {
            try {
                val s1 = oper1()
                val s2 = oper2()
                setResult("($s1) and ($s2)")
            } catch (ex: Throwable) {
                log.warn("catching '{}'", ex.message)
                setErrorResult(ex)
            }
        }
        log.info("returning from controller handler")
    }

    @GetMapping("/example4")
    fun get4() = DeferredResultScope<String>(5000).apply {
        log.info("starting controller handler")
        this.launch {
            try {
                val res = coroutineScope {
                    val d1 = async { oper1() }
                    val d2 = async { oper2() }
                    "(${d1.await()}) and (${d2.await()})"
                }
                log.info("setting result $res")
                setResult(res)
            } catch (ex: Throwable) {
                log.warn("catching '{}'", ex.message)
                setErrorResult(ex)
            }
        }
        log.info("returning from controller handler")
    }

    @GetMapping("/example5")
    fun get5() = DeferredResultScope<String>(1000).apply {
        log.info("starting controller handler")
        this.launch {
            try {
                val res = coroutineScope {
                    val d1 = async { oper1() }
                    val d2 = async { oper2() }
                    "(${d1.await()}) and (${d2.await()})"
                }
                log.info("setting result $res")
                setResult(res)
            } catch (ex: Throwable) {
                log.warn("catching '{}'", ex.message)
                setErrorResult(ex)
            }
        }
        log.info("returning from controller handler")
    }

    @GetMapping("/example6")
    fun get6() = DeferredResultScope<String>(5000).apply {
        log.info("starting controller handler")
        this.launch {
            try {
                val res = coroutineScope {
                    val d1 = async { oper1() }
                    val d2 = async { oper3() }
                    "(${d1.await()}) and (${d2.await()})"
                }
                log.info("setting result $res")
                setResult(res)
            } catch (ex: Throwable) {
                log.warn("catching '{}'", ex.message)
                setErrorResult(ex)
            }
        }
        log.info("returning from controller handler")
    }

    @GetMapping("/example7")
    fun get7() = asyncHandler(5000) {
        val res = coroutineScope {
            val d1 = async { oper1() }
            val d2 = async { oper3() }
            "(${d1.await()}) and (${d2.await()})"
        }
        res
    }

    @GetMapping("/example8")
    fun get8() = asyncHandler(5000) {
        val res = coroutineScope {
            val d1 = async { oper1() }
            val d2 = async { oper2() }
            "(${d1.await()}) and (${d2.await()})"
        }
        res
    }

    @GetMapping("/example9")
    fun get9() = asyncHandler(1000) {
        val res = coroutineScope {
            val d1 = async { oper1() }
            val d2 = async { oper2() }
            "(${d1.await()}) and (${d2.await()})"
        }
        res
    }

}

// Helper functions below...

private fun <T> asyncHandler(
        ms: Long? = null,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> T) = DeferredResultScope<T>(ms).apply {
    log.info("starting controller handler")
    this.launch(context) {
        try {
            val res = this.block()
            log.info("completing asyncHandler successfully")
            setResult(res)
        } catch (ex: Throwable) {
            log.info("completing asyncHandler with exception '{}'", ex.message)
            setErrorResult(ex)
        }
    }
    log.info("returning from controller handler")
}

class DeferredResultScope<T>(private val timeout: Long? = null)
    : DeferredResult<T>(timeout), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job

    init {
        this.onTimeout {
            log.info("DeferredResult timed out")
            setErrorResult(AsyncRequestTimeoutException())
            job.cancel()
        }
    }
}

// Just for demo purposes ...

private suspend fun oper1(): String {
    try {
        delay(2000)
        log.info("`oper1` ending")
        return "result from `oper1`"
    } catch (ex: CancellationException) {
        log.warn("`oper1` cancelled")
        throw ex
    }
}

private suspend fun oper2(): String {
    try {
        delay(2500)
        log.info("`oper2` ending")
        return "result from `oper2`"
    } catch (ex: CancellationException) {
        log.warn("`oper2` cancelled")
        throw ex
    }
}

private suspend fun oper3(): String {
    try {
        delay(500)
        log.warn("`oper3` ending with error")
        throw Exception("error on `oper3`")
    } catch (ex: CancellationException) {
        log.warn("`oper3` cancelled")
        throw ex
    }
}





