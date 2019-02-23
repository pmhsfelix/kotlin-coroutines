// Introductory examples for the Kotlin Coroutines talk on BuildStuff 18

package org.pedrofelix.kotlin.examples

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.*

private val log = LoggerFactory.getLogger("intro")

fun blockingOperation(input: Int): Int {
    Thread.sleep(1000)
    return input + 42
}

private val executor = Executors.newScheduledThreadPool(5)
fun asyncOperationWithCallback(input: Int, callback: (Int) -> Unit): Unit {
    executor.schedule({ callback(input + 42) }, 1000, TimeUnit.MILLISECONDS)
}

fun asyncOperationReturningFuture(input: Int) = CompletableFuture<Int>().apply {
    val scheduledFuture = executor.schedule({ this.complete(input + 42) }, 1000, TimeUnit.MILLISECONDS)
    this.handle {_, ex ->
        if(this.isCancelled){
            scheduledFuture.cancel(true)
        }
    }
}

fun doSomethingWith(input: Int): Int {
    log.info("doing something with {}", input)
    return input + 1
}

val `?` = 0

fun example0(input: Int): Int {
    var acc = input
    for (i in 0..2) {
        val result = blockingOperation(acc)
        acc = doSomethingWith(result)
    }
    return acc
}

fun example1(input: Int): Int {
    var acc = input
    for (i in 0..2) {
        asyncOperationWithCallback(acc) { result -> `?` }
        acc = doSomethingWith(`?`)
    }
    return acc
}

fun example2(input: Int): Int {
    var acc = input
    for (i in 0..2) {
        asyncOperationWithCallback(acc) { result ->
            // different scope
            // outside of for loop
            acc = doSomethingWith(result)
        }
        // acc = doSomethingWith(result)
    }
    return acc
}

fun example3(input: Int): Int {
    var acc = input
    for (i in 0..2) {
        val future = asyncOperationReturningFuture(acc)
        acc = doSomethingWith(future.get()) // now it's blocking again
    }
    return acc
}

fun example4(input: Int): Int {
    var acc = input
    for (i in 0..2) {
        val future = asyncOperationReturningFuture(acc)
        future.thenAccept { result ->
            // same issue than with callbacks
            acc = doSomethingWith(result)
        }
    }
    return acc
}

suspend fun example5(input: Int): Int {
    var acc = input
    for (i in 0..2) {
        val future = asyncOperationReturningFuture(acc)
        val result = suspendCoroutine<Int> { continuation ->
            future.thenAccept { result ->
                continuation.resume(result)
            }
        }
        acc = doSomethingWith(result)
    }
    return acc
}

/*
suspend fun example5(input: Int): CompletableFuture<Int> {
    var acc = input
    for (i in 0..2) {
        val result = await asyncOperationReturningFuture(acc)
        acc = doSomethingWith(result)
    }
    return acc
}
*/

private suspend fun <T> CompletableFuture<T>.await2(): T = suspendCoroutine { cont ->
    whenComplete { value, error ->
        if (error != null)
            cont.resumeWithException(error)
        else
            cont.resume(value)
    }
}

suspend fun example6(input: Int): Int {
    var acc = input
    for (i in 0..2) {
        val future = asyncOperationReturningFuture(acc)
        val result = future.await()
        acc = doSomethingWith(result)
    }
    return acc
}

suspend fun example7(input: Int): Int {
    var acc = input
    for (i in 0..2) {
        log.info("before asyncOperationReturningFuture")
        val result = asyncOperationReturningFuture(acc).await()
        log.info("after asyncOperationReturningFuture")
        acc = doSomethingWith(result)
    }
    return acc
}

suspend fun composingSuspendFunctions(input: Int): Int {
    var acc = 0
    for (i in 0..2) {
        acc = example7(acc)
    }
    return acc
}

suspend fun example8(input: Int): Int {
    var acc = input
    for (i in 0..2) {
        val result = suspendCoroutine<Int> { cont ->
            asyncOperationWithCallback(acc) {
                cont.resume(it)
            }
        }
        acc = doSomethingWith(result)
    }
    return acc
}

class StateMachine(input: Int, val continuation: Continuation<Int>) {
    var acc = input
    var i = 0
    var state = 0
    var result = 0

    fun start() {
        state = 0
        run()
    }

    private fun run() {
        while (true) {
            when (state) {
                0 -> {
                    if (i > 2) {
                        continuation.resume(acc)
                        return
                    }
                    state = 1
                }
                1 -> {
                    asyncOperationReturningFuture(acc).thenAccept { res ->
                        result = res
                        state = 2
                        run()
                    }
                    return;
                }
                2 -> {
                    acc = doSomethingWith(result)
                    i += 1
                    state = 0
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    //usingExample0()
    //usingAStateMachine()
    //usingASuspendFunction5()
    runBlocking {
        async(Dispatchers.Unconfined) {
            example7(0)
        }
    }

    Thread.sleep(4000)
    executor.shutdown()
}

private fun usingExample0() {
    log.info("result is {}", example0(1))
}

private fun usingASuspendFunction() {
    // example7(1)
    // Error: Suspend function '...' should be called only from a coroutine or another suspend function
    ::example7.startCoroutine(1, object : Continuation<Int> {
        override fun resumeWith(result: Result<Int>) {
            log.info("result is {}", result.getOrThrow())
        }

        override val context: CoroutineContext
            get() = Unconfined
    })
}

private fun usingASuspendFunction2() {
    val future = CompletableFuture<Int>()
    ::example7.startCoroutine(1, object : Continuation<Int> {
        override fun resumeWith(result: Result<Int>) {
            result
                    .onSuccess { future.complete(it) }
                    .onFailure { future.completeExceptionally(it) }
        }

        override val context: CoroutineContext
            get() = Unconfined
    })
    future.thenApply { log.info("result is {}", it) }
}

private fun usingASuspendFunction3() {
    val deferred = GlobalScope.async {
        example7(1)
    }
    deferred.invokeOnCompletion {
        object : CompletionHandler {
            override fun invoke(cause: Throwable?) {
                log.info("result is {}", deferred.getCompleted())
            }
        }
    }
}

private fun usingASuspendFunction4() {
    val future = CompletableFuture<Int>()
    val deferred = GlobalScope.async {
        try {
            future.complete(example7(1))
        } catch (ex: Throwable) {
            future.completeExceptionally(ex)
        }
    }
    future.thenApply { log.info("result is {}", it) }
}

private fun usingASuspendFunction5() {
    val future = GlobalScope.future {
        example7(1)
    }
    future.thenApply { log.info("result is {}", it) }
}

private fun usingAStateMachine() {
    val continuation = object : Continuation<Int> {
        override fun resumeWith(result: Result<Int>) {
            log.info("result is {}", result.getOrThrow())
        }

        override val context: CoroutineContext
            get() = TODO("not implemented")
    }
    val stateMachine = StateMachine(1, continuation)
    stateMachine.start()
    Thread.sleep(4000)
}


