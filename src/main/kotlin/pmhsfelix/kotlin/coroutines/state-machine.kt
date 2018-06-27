package pmhsfelix.kotlin.coroutines

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("intro")

suspend fun suspendFunctionWithDelay3(a: Int, b: Int): Int {
    log.info("step 1")
    delay(1000)
    log.info("step 2")
    delay(2000)
    log.info("step 3")
    return a + b
}