package pmhsfelix.kotlin.coroutines

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.future.future
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.*

private val log = LoggerFactory.getLogger("intro")

fun decorateContext(ctx: CoroutineContext): CoroutineContext =
        ctx + decorateInterceptor(ctx[ContinuationInterceptor.Key])

fun decorateInterceptor(interceptor: ContinuationInterceptor?):  ContinuationInterceptor =
        object : ContinuationInterceptor, AbstractCoroutineContextElement(ContinuationInterceptor) {
            override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
                    log("interceptContinuation ${continuation.hashCode()}") {
                        interceptor?.interceptContinuation(loggingContinuation(continuation))
                                ?: loggingContinuation(continuation)
                    }

        }

fun <T> loggingContinuation(continuation: Continuation<T>) = object : Continuation<T> by continuation {
    override fun resume(value: T) {
       log("resume") {
           continuation.resume(value)
       }
    }

    override fun resumeWithException(exception: Throwable) {

    }
}

fun <T> log(msg: String, block: ()->T) : T {
    log.info("before $msg")
    try {
        return block()
    }finally{
        log.info("after $msg")
    }
}


suspend fun delayWithLog(ms: Long) {
    suspendCoroutine<Unit> { continuation ->
        log.info("scheduling continuation ${continuation.hashCode()}")
        executor.schedule({ continuation.resume(Unit) }, ms, TimeUnit.MILLISECONDS)
    }
    log.info("delay: after suspendCoroutine")
}

fun main(args: Array<String>) = runBlocking {

    val job = launch (decorateContext(CommonPool)) {
        log.info("step 1")
        delayWithLog(1000)
        log.info("step 2")
        delayWithLog(1000)
        log.info("step 3")
    }
    job.join()

}


