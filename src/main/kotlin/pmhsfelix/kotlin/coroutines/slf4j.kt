package pmhsfelix.kotlin.coroutines

import kotlinx.coroutines.experimental.*
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import kotlin.coroutines.experimental.AbstractCoroutineContextElement
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.ContinuationInterceptor
import kotlin.coroutines.experimental.CoroutineContext

private val log = LoggerFactory.getLogger("intro")

fun main(args: Array<String>) = runBlocking {
    val key1 = "Key1"
    val key2 = "Key2"
    val key3 = "Key3"
    val context = CommonPool

    val jobs = List(10) { ix ->
        MDC.put(key1, ix.toString())
        launch(preserveMDC(context)) {
            log.info("expected = {}, key1 = {}", ix, MDC.get(key1))
            MDC.put(key2, ix.toString())
            delay(1000)
            log.info("expected = {}, key1 = {}, key2 = {}", ix, MDC.get(key1), MDC.get(key2))
            MDC.put(key3, ix.toString())
            delay(1000)
            log.info("expected = {}, key1 = {}, key2 = {}, key3 = {}", ix, MDC.get(key1), MDC.get(key2), MDC.get(key3))
        }
    }
    jobs.forEach { it.join() }
}


fun preserveMDC(ctx: CoroutineContext): CoroutineContext {
    val currentInterceptor = ctx[ContinuationInterceptor.Key]
    if(currentInterceptor != null && currentInterceptor is Slf4jInterceptor) {
        //return ctx
    }
    val capturedMDC = MDC.getCopyOfContextMap()
    if(capturedMDC == null || capturedMDC.isEmpty()) {
        return ctx
    }
    val originalInterceptor = ctx[ContinuationInterceptor.Key] ?: NopInterceptor()
    return ctx + Slf4jInterceptor(originalInterceptor, capturedMDC)
}

private class Slf4jInterceptor(val originalInterceptor: ContinuationInterceptor, val capturedContext: MutableMap<String, String>)
    : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {

    var currentContext = capturedContext
    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
            originalInterceptor.interceptContinuation(object : Continuation<T> {

                override val context: CoroutineContext
                    get() = continuation.context

                override fun resume(value: T) {
                    currentContext = useMDC(currentContext) {
                        continuation.resume(value)
                    }
                }

                override fun resumeWithException(exception: Throwable) {
                    currentContext = useMDC(currentContext) {
                        continuation.resumeWithException(exception)
                    }
                }
            })

}

private class NopInterceptor : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> = continuation
}

private inline fun <T> useMDC(mdc: MutableMap<String, String>, block: () -> T): MutableMap<String, String> {
    val original = MDC.getCopyOfContextMap()
    MDC.setContextMap(mdc)
    try {
        block()
        return MDC.getCopyOfContextMap()
    } finally {
        MDC.setContextMap(original)
    }
}