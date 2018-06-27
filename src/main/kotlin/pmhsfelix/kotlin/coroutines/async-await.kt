package pmhsfelix.kotlin.coroutines

import kotlinx.coroutines.experimental.future.await
import kotlinx.coroutines.experimental.future.future
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger("intro")

fun <T> asyncDelay(ms: Long, value: T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    executor.schedule({
        future.complete(value)}
            , ms, TimeUnit.MILLISECONDS)
    return future
}

fun main(args : Array<String>) {
    log.info("main started")
    //val f = future(Unconfined) {
    //    val v1 = asyncDelay(1000, 1).await()
    //    log.info("v1 is $v1")
    //    val v2 = asyncDelay(1000, v1 + 1).await()
    //    log.info("v2 is $v2")
    //    v1 + v2
    //}
    val f = future {
        val f1 = asyncDelay(1000, 1)
        val f2 = asyncDelay(1000, 1)
        f1.await() + f2.await()
    }

    log.info("before call to f.get()");
    log.info("final result is {}", f.get())
    executor.shutdown()
    log.info("main ended")
}