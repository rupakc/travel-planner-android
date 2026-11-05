package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.data.di.ApplicationScopeModule
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review I7: a failure in app-scope work is logged and the scope keeps working. */
class ApplicationScopeTest {
    private val previous = AppLogger.sink

    @After fun restore() {
        AppLogger.sink = previous
    }

    @Test
    fun `uncaught failure is logged and the scope survives`() =
        runBlocking {
            val logged = mutableListOf<Throwable>()
            AppLogger.sink = AppLogger.Sink { _, _, _, t -> t?.let { logged += it } }
            val scope = ApplicationScopeModule.applicationScope(TestAppDispatchers(Dispatchers.Default))
            scope.launch { throw IllegalStateException("boom") }.join()
            var ran = false
            scope.launch { ran = true }.join()
            assertEquals("boom", logged.single().message)
            assertTrue(ran)
        }
}
