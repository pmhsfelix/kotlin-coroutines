package pmhsfelix.kotlin.coroutines

import java.util.concurrent.CompletableFuture
import kotlin.coroutines.experimental.suspendCoroutine

suspend fun <T> CompletableFuture<T>.await(): T = suspendCoroutine<T> { cont ->
    this.whenComplete({value, error ->
        if (error != null)
            cont.resumeWithException(error)
        else
            cont.resume(value)
})}
