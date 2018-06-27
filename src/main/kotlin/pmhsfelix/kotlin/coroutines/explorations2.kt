package pmhsfelix.kotlin.coroutines

import kotlinx.coroutines.experimental.Unconfined
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.startCoroutine
import kotlin.coroutines.experimental.suspendCoroutine

//private val executor = ScheduledThreadPoolExecutor(1)

fun show(s : String) : Unit = println("${Thread.currentThread().name}, ${System.currentTimeMillis()}: $s")

fun aFunction () : String {
    show("first step")
    show("second step")
    return "done"
}

fun aFunction2 () : String {
    show("first step")
    Thread.sleep(1000)
    show("second step")
    return "done"
}

suspend fun aSuspendableFunction() : String {
    show("first step")
    suspendCoroutine<Unit> { cont ->
        executor.schedule<Unit>({ cont.resume(Unit) }, 1000, TimeUnit.MILLISECONDS)
    }
    show("second step")
    return "done"
}



fun main(args : Array<String>) {
    //show(aFunction2())
    start { aSuspendableFunction() }

}


