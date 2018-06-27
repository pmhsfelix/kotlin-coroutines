package pmhsfelix.kotlin.coroutines

import kotlinx.coroutines.experimental.Unconfined
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.startCoroutine
import kotlin.coroutines.experimental.suspendCoroutine

class A {}
class B {}
class C {}
class D {}

fun g(a : A) : B = B()
fun h(b : B) : C = C()
fun l(c : C) : D = D()
suspend fun hs(b : B) : C = C()
fun hf(b : B) : CompletableFuture<C> {
    val cf = CompletableFuture<C>()
    cf.complete(C())
    return cf
}

val x : (A, (B) -> Unit) -> B? = {_,_ -> B()}

fun f(a : A) : D {
    val x = g(a)
    val y = h(x)
    return l(y)
}

suspend fun fs(a : A) : D {
    val x = g(a)
    val y = h(x)
    return l(y)
}

suspend fun fs2(a : A) : D {
    val x = g(a)
    val y = hs(x)
    return l(y)
}

suspend fun fs3(a : A) : D {
    val x = g(a)
    val fy = hf(x)
    val y = suspendCoroutine<C> { cont -> fy.thenApply({v -> cont.resume(v)}) }
    return l(y)
}

fun start (block: suspend (A)->D, a : A ) : D {
    val cf = CompletableFuture<D>()
    block.startCoroutine(a, object : Continuation<D> {
        override fun resume(value: D) {
            cf.complete(value)
        }

        override fun resumeWithException(exception: Throwable) {
            cf.completeExceptionally(exception)
        }

        override val context: CoroutineContext
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    })
    return cf.get()
}

fun <T> createSequence(block: suspend MySequence<T>.() -> Unit) : MySequence<T> {
    val seq = MySequence<T>()
    val ctx = Unconfined
    block.startCoroutine<MySequence<T>, Unit>(seq, object : Continuation<Unit> {
        override val context: CoroutineContext
            get() = ctx

        override fun resume(value: Unit) {
            seq.stop()
        }
        override fun resumeWithException(exception: Throwable) {
            seq.stop()
        }
    })
    return seq
}

class MySequence<T> : Sequence<T>, Iterator<T> {
    var c : Continuation<Unit>? = null
    var value : T? = null
    var hasValue = false
    var ended = false

    suspend fun yield(value : T) {
        println("yield $value")
        this.value = value
        this.hasValue = true
        suspendCoroutine<Unit> { cont ->
            c = cont
        }
    }

    fun stop() { ended = true }

    override fun next(): T {
        hasValue = false
        return value!!
    }

    override fun hasNext(): Boolean {
        if(!hasValue) {
            c!!.resume(Unit)
        }
        return !ended
    }

    override fun iterator(): Iterator<T> = this

}

fun main(args : Array<String>) {
    val seq = createSequence {
        yield(1)
        yield(2)
        yield(3)
    }
    println("before for")
    for(v in seq ){
        println(v)
    }
}


