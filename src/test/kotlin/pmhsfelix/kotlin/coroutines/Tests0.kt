package pmhsfelix.kotlin.coroutines

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class Tests0 {
    @Test
    fun testMySuspendingFunction() = runBlocking<Unit> {
        val v = 2
        delay(1000)
        assertEquals(2, v)
    }
}

