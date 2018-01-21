# An introduction to Kotlin Coroutines #

## Motivation ##

TBD

## Coroutines and suspendible functions ##

Let's start our journey into coroutines with a simple regular function that takes some parameters, performs two steps and returns a value.

```kotlin
fun simpleFunction(a: Int, b: Int): Int {
    log.info("step 1")
    log.info("step 2")
    return a + b
}
```

Calling this function from our `main` entry point function

```kotlin
fun main(args: Array<String>) {
    log.info("main started")
    log.info("result is {}", simpleFunction(40, 2))
    log.info("main ended")
}
```

produces the following

```
8 [main] INFO intro - main started
9 [main] INFO intro - step 1
9 [main] INFO intro - step 2
10 [main] INFO intro - result is 42
10 [main] INFO intro - main ended
```

The value between brackets contains the thread names where the `log.info` calls were performed: in this case all were performed on the so called `main` thread (the one that calls the `main` method).

The following diagram illustrates the execution of `simpleFunction`, highlighting the fact that all statements are executed in same thread.

![simpleFunction](coroutines-intro-1.png)

Now, suppose that between `step 1` and `step 2` we need to wait for something to happen, such as receiving a message from an external system or waiting for a time period to elapse.
To keep things simple lets illustrate that using a `Thread.sleep`

```kotlin
fun simpleFunctionWithDelay(a: Int, b: Int): Int {
    log.info("step 1")
    Thread.sleep(1000)
    log.info("step 2")
    return a + b
}
```

Running this function from our main function produces the following output

```
7 [main] INFO intro - main started
8 [main] INFO intro - step 1
1011 [main] INFO intro - step 2
1012 [main] INFO intro - result is 42
1012 [main] INFO intro - main ended
```

Again, all the statements are run on the `main` thread, which blocks for 1000 ms between `step 1` and `step 2`.

![simpleFunctionWithDelay](coroutines-intro-2.png)

However, blocking threads may not be a good thing:

* On client applications (e.g. Android applications), if the blocked thread is the UI thread then the application will become unresponsive during the blocking period.

* On server applications, blocking threads will reduce the number of requests that can be served simultaneously.

Kotlin **suspendible** functions provide us with a way of handling these *pauses* in a sequential flow of statements without blocking the hosting thread. 
Namely, it allows a function 
* to suspend its execution, by returning immediately to its caller and free up the hosting thread.
* resume its execution in a future point in time, potentially on a different thread.

So, lets convert the previous `simpleFunctionWithDelay` example to a suspendible function that does not block the hosting thread.

```kotlin
suspend fun suspendFunctionWithDelay(a: Int, b: Int): Int {
    log.info("step 1")
    suspendCoroutine<Unit> { cont ->
        executor.schedule(
          { cont.resume(Unit) }, 
          1000,TimeUnit.MILLISECONDS)
    }
    log.info("step 2")
    return a + b
}
```

The first thing to notice is that a suspendible function declaration is prefixed with the `suspend` keywords.
However, the remaining function signature is unchanged: it still receives two integers and returns an integer.

Looking into the function body we notice that it remains mostly unchanged, except for the `Thread.sleep` call that was replaced by a call to `suspendCoroutine`.

This `suspendCoroutine` function, available in the `kotlin.coroutines.experimental` package, is used to *suspend* the function where it is invoked.
It has the following signature

```kotlin
public inline suspend fun <T> suspendCoroutine(crossinline block: (Continuation<T>) -> Unit): T
```

The main thing to understand is the `block` parameter, which is a function that receives a *continuation* representing the resume point after the suspension.
A continuation is an object implementing the following interface

```kotlin
public interface Continuation<in T> {
    /**
     * Resumes the execution of the corresponding coroutine passing [value] as the return value of the last suspension point.
     */
    public fun resume(value: T)

    /**
     * Resumes the execution of the corresponding coroutine so that the [exception] is re-thrown right after the
     * last suspension point.
     */
    public fun resumeWithException(exception: Throwable)

    /**
     * Context of the coroutine that corresponds to this continuation.
     */
    public val context: CoroutineContext
}
```

Ignoring the `context` field for the moment being, a `Continuation<T>` has two members: 
* the `resume` function, to be called if the suspendible function should resume normally with a value; 
* and the `resumeWithException` function to be called if the suspendible function should resume with an exception.

In our case, the continuation will _point_ to the statement `log.info("step 2")`, i.e., the statement after the point where the function called `suspendCoroutine`.
It is the responsibility of the function invoking `suspendCoroutine` to pass a `block` that does something with that continuation.

In our case, we just we use a plain Java `ScheduledExecutorService` 
```kotlin
val executor = Executors.newScheduledThreadPool(1)
```

to schedule the execution of the continuation after 1000 ms.
```kotlin
suspendCoroutine<Unit> { cont ->
        executor.schedule(
          { cont.resume(Unit) }, 
          1000,TimeUnit.MILLISECONDS)
    }
```

The `{ cont.resume(Unit) }` is just the `Runnable` passed into the executor.

The following diagram illustrates this suspension and resumption.

![suspendFunctionWithDelay](coroutines-intro-3.png)

The `suspendCoroutine` function calls the passed in user function, which schedules the `cont.resume(Unit)` to be run in 1000 ms, and then terminates, freeing up the main thread (in green color).
After the 1000 ms elapse, a thread from the scheduled pool (in orange color) calls `cont.resume(Unit)`, resuming the execution of the `suspendFunctionWithDelay`.

This suspension and resumption, including the switch between threads, is visible in the program output 

```
8 [main] INFO intro - main started
20 [main] INFO intro - step 1
24 [main] INFO intro - main ended
1027 [pool-1-thread-1] INFO intro - step 2
1029 [pool-1-thread-1] INFO intro - result is 42
```

Notice that the `main` function ends immediately after `step 1`, without waiting for the 1000 ms to elapse, because `suspendFunctionWithDelay` suspended its execution and *returns* to the `main` function.
After the 1000 ms elapses, the `suspendFunctionWithDelay` resumes its execution in the `pool-1-thread-1` (a thread from the scheduled pool) and `step 2` is executed.

Using `suspendCoroutine` directly in our `suspendFunctionWithDelay` makes the code a slightly brittle to read, namely due to the nested lambda passed as parameter.
However, that can be easily handled by wrapping that behavior on an helper suspendible function

```kotlin
suspend fun delay(ms: Long) {
    suspendCoroutine<Unit> { continuation ->
        executor.schedule({ continuation.resume(Unit) }, ms, TimeUnit.MILLISECONDS)
    }
}
```

The `suspendFunctionWithDelay` now becomes
```kotlin
suspend fun suspendFunctionWithDelay2(a: Int, b: Int): Int {
    log.info("step 1")
    delay(1000)
    log.info("step 2")
    return a + b
}
```
which is as readable as our initial `simpleFunctionWithDelay` that used `Thread.sleep`, however has a non-blocking behavior.

Suspendible functions can call regular functions or other suspendible functions.
However, suspendible functions cannot be be called directly from regular functions.
Namely, our regular `main` function cannot call `suspendFunctionWithDelay` directly.
For that we need to use another function provided by the Kotlin library named `startCoroutine`, which is an regular (i.e. non-suspendible) extension function over a `suspendible lambda`.

```kotlin
public fun <T> (suspend  () -> T).startCoroutine(
        completion: Continuation<T>
)
```

The `startCoroutine` receives:
* the suspendible lambda to start (as the extension target).
* the continuation to use when the suspendible function completes its execution.

Using it we can create a simple `startAndForget` function
```kotlin
fun startAndForget(suspendibleFunction: suspend () -> Unit) {
    suspendibleFunction.startCoroutine(object : Continuation<Unit> {
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
```
that starts a suspendible function and ignores its result.

We are now in condition to show the `main` function using the suspendible version
```kotlin
fun main(args: Array<String>) {
    log.info("main started")
    startAndForget {
        log.info("result is {}", suspendFunctionWithDelay2(40, 2))
    }
    log.info("main ended")
}
```

which produces the output already shown before

```
8 [main] INFO intro - main started
20 [main] INFO intro - step 1
24 [main] INFO intro - main ended
1027 [pool-1-thread-1] INFO intro - step 2
1029 [pool-1-thread-1] INFO intro - result is 42
```

Notice how the `result is 42` log message appears after the `main` function is terminated.

The following diagram depicts the complete picture, including the `log.info` with the  `suspendFunctionWithDelay2` returned value, as well as the final continuation.
![suspendFunctionWithDelay](coroutines-intro-4.png)

The continuation passed in to startCoroutine allow us to do more interesting things than just ignoring the result.
For instance, the following example uses a `CompletableFuture` to allow the `main` function to synchronize with the completable function termination.

```kotlin
fun startAndGetFuture(suspendibleFunction: suspend () -> Unit): CompletableFuture<Unit>{
    val future = CompletableFuture<Unit>()
    suspendibleFunction.startCoroutine(object : Continuation<Unit> {
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
```

With this, we can rewrite `main` to synchronize with the future completion.
We use a blocking `future.get()` but on a real scenario we could also use a non-blocking `future.thenApply`.

```kotlin
fun main(args: Array<String>) {
    log.info("main started")
    val future = startAndGetFuture {
        log.info("result is {}", suspendFunctionWithDelay2(40, 2))
    }
    future.get()
    executor.shutdown()
    log.info("main ended")
}
```

Running this `main` function produced

```
8 [main] INFO intro - main started
16 [main] INFO intro - step 1
1022 [pool-1-thread-1] INFO intro - step 2
1024 [pool-1-thread-1] INFO intro - result is 42
1025 [main] INFO intro - main ended
```

Notice how the `main` function only ends after the suspendible function completely terminate (i.e. prints `result is 42`).

Until now, and based solely on this simple example, all this suspendible mechanics may seem a rather complex way to achieve something that could be done using a simple callback.
However, the advantage of the coroutine mechanism starts to be apparent when the suspendible functions is more than just an unconditional sequence of steps, such as the following example.

```kotlin
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
```

In this case, the produced output is

```
8 [main] INFO intro - main started
17 [main] INFO intro - step 1 of iteration 0
1024 [pool-1-thread-1] INFO intro - step 2 of iteration 0
1024 [pool-1-thread-1] INFO intro - step 1 of iteration 1
1024 [pool-1-thread-1] INFO intro - step 2 of iteration 1
1024 [pool-1-thread-1] INFO intro - step 1 of iteration 2
2028 [pool-1-thread-1] INFO intro - step 2 of iteration 2
2028 [pool-1-thread-1] INFO intro - step 1 of iteration 3
2028 [pool-1-thread-1] INFO intro - step 2 of iteration 3
2031 [pool-1-thread-1] INFO intro - result is 42
2032 [main] INFO intro - main ended
```

