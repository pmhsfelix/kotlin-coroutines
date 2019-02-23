package org.pedrofelix.kotlin.examples

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Test
import rx.Single

class RxExampleTests {

    @Test
    fun test() {

        val s = Single.create<String> { obs ->
            val job = GlobalScope.launch {
                delay(1000)
                obs.onSuccess("done")
            }
            // ...
            job.cancel()
        }
    }
}