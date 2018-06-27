package pmhsfelix.kotlin.coroutines

import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.*

private val log = LoggerFactory.getLogger("intro")

fun simpleFunction(a: Int, b: Int): Int {
    log.info("step 1")
    log.info("step 2")
    return a + b
}

fun simpleFunctionWithABlockingWait(a: Int, b: Int): Int {
    log.info("step 1")
    Thread.sleep(1000);
    log.info("step 2")
    return a + b
}

val executor = Executors.newScheduledThreadPool(4)
suspend fun suspendFunctionNonBlockingWait(a: Int, b: Int): Int {
    log.info("step 1")
    suspendCoroutine<Unit> { cont ->
        executor.schedule({ cont.resume(Unit) }, 1000, TimeUnit.MILLISECONDS)
    }
    log.info("step 2")
    return a + b
}

suspend fun delay(ms: Long) {
    suspendCoroutine<Unit> { continuation ->
        executor.schedule({ continuation.resume(Unit) }, ms, TimeUnit.MILLISECONDS)
    }
}
suspend fun suspendFunctionNonBlockingWait2(a: Int, b: Int): Int {
    log.info("step 1")
    delay(1000)
    log.info("step 2")
    return a + b
}

suspend fun suspendFunctionWithDelayAndALoopWithConditionalLogic(a: Int, b: Int): Int {
    for(i in 0..3) {
        log.info("step 1 of iteration $i")
        if(i % 2 == 0) {
            delay(1000)
        }
        log.info("step 2 of iteration $i")
    }
    return a + b
}

fun <T> start(f: suspend () -> T) {
    f.startCoroutine(object : Continuation<T> {
        override val context: CoroutineContext
            get() = Unconfined

        override fun resume(value: T) {
            log.info("result is {}", value)
        }

        override fun resumeWithException(exception: Throwable) {
            log.error("ended with exception", exception)
        }
    })
}

fun startAndForget(suspendableFunction: suspend () -> Unit) {
    suspendableFunction.startCoroutine(object : Continuation<Unit> {
        override fun resume(value: Unit) {
            // forget it
        }

        override fun resumeWithException(exception: Throwable) {
            // forget it
        }

        override val context: CoroutineContext
            get() = EmptyCoroutineContext
    })
}

fun startAndGetFuture(suspendableFunction: suspend () -> Unit): CompletableFuture<Unit>{
    val future = CompletableFuture<Unit>()
    suspendableFunction.startCoroutine(object : Continuation<Unit> {
        override fun resume(value: Unit) {
            future.complete(value)
        }

        override fun resumeWithException(exception: Throwable) {
            future.completeExceptionally(exception)
        }

        override val context: CoroutineContext
            get() = EmptyCoroutineContext
    })
    return future
}

fun main2(args: Array<String>) {
    log.info("main started")
    //log.info("result is {}", simpleFunction(40, 2))
    //log.info("result is {}", simpleFunctionWithDelay(40, 2))
    //startAndForget {
    //    log.info("result is {}", suspendFunctionWithDelay2(40, 2))
    //}
    val future = startAndGetFuture {
        log.info("result is {}", suspendFunctionWithDelayAndALoopWithConditionalLogic(40, 2))
    }
    //val future = startFuture {
    //    repeat(4) {
    //        log.info("result is {}", suspendFunctionWithDelay2(40, 2))
    //    }
    //}
    future.get()

    executor.shutdown()
    log.info("main ended")
}

fun main3(args: Array<String>) {
    log.info("main started")
    startAndForget {
        log.info("result is {}", suspendFunctionWithDelayAndALoopWithConditionalLogic(40, 2))
    }
    executor.schedule({}, 1000, TimeUnit.MILLISECONDS)
    executor.schedule({}, 2000, TimeUnit.MILLISECONDS)
    executor.schedule({}, 3000, TimeUnit.MILLISECONDS)
    //executor.shutdown()
    log.info("main ended")
}

fun main(args: Array<String>) {
    log.info("main started")
    runBlocking(Unconfined) {
        //showTagsForReposInOrgs("https://api.github.com/users/ktorio/repos")
        showTagsForReposInOrgsUsingAsyncHttpClient("https://api.github.com/users/ktorio/repos")
    }
    log.info("main ended")
}




