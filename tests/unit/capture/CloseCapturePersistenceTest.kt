package tests.unit.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import plugin.capture.TerminalSnapshotCapture
import plugin.adapters.TerminalTabState
import plugin.capture.resolution.ProcessMetadata
import plugin.persistence.SnapshotMigrationService
import plugin.persistence.SnapshotSerializer
import plugin.persistence.TerminalSessionSnapshotStore
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Guards the close-path persistence guarantee: after the project-closing capture path runs, the
 * snapshot store must contain the captured state. The previous fire-and-forget implementation
 * could lose the last snapshot if the pooled thread was killed on IDE shutdown before its write
 * completed; these tests pin the new synchronous-persist behaviour.
 */
class CloseCapturePersistenceTest {
    private val scopeId = "project-close"

    private fun newStore(): Pair<TerminalSessionSnapshotStore, java.io.File> {
        val tempDir = createTempDir(prefix = "close-capture-")
        val store = TerminalSessionSnapshotStore(SnapshotSerializer(), SnapshotMigrationService(), tempDir)
        return store to tempDir
    }

    @Test
    fun `snapshot is persisted before close returns when resolution completes`() {
        val (store, tempDir) = newStore()
        val rawTabs = listOf(
            TerminalSnapshotCapture.rawTabForTest(
                index = 0,
                title = "Codex Tab",
                typedCommand = "codex resume my-session",
                processMetadata = ProcessMetadata(123L, "codex resume my-session"),
            ),
        )

        // Executor runs synchronously on the calling thread, modelling a fast resolution.
        val executor: (Callable<List<TerminalTabState>>) -> Future<List<TerminalTabState>> = { callable ->
            CompletableFuture.completedFuture(callable.call())
        }

        TerminalSnapshotCapture.awaitAndPersist(store, scopeId, "/tmp/project", rawTabs, executor, resolutionTimeoutMs = 5000)

        val loaded = store.load(scopeId, 3)
        assertEquals(1, loaded.size)
        assertEquals("Codex Tab", loaded.first().terminalDisplayName)
        tempDir.deleteRecursively()
    }

    @Test
    fun `snapshot is still persisted when background resolution times out`() {
        val (store, tempDir) = newStore()
        val rawTabs = listOf(
            TerminalSnapshotCapture.rawTabForTest(
                index = 0,
                title = "Hung Tab",
                typedCommand = "codex",
                processMetadata = ProcessMetadata(99L, "codex"),
            ),
        )

        val released = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        // Executor never finishes within the timeout, modelling a hung `opencode session list`.
        val executor: (Callable<List<TerminalTabState>>) -> Future<List<TerminalTabState>> = { callable ->
            pool.submit(Callable { released.await(10, TimeUnit.SECONDS); callable.call() })
        }

        TerminalSnapshotCapture.awaitAndPersist(store, scopeId, "/tmp/project", rawTabs, executor, resolutionTimeoutMs = 100)

        // Even though resolution never returned, the close path must have written a snapshot.
        val loaded = store.load(scopeId, 3)
        assertEquals(1, loaded.size)
        assertEquals("Hung Tab", loaded.first().terminalDisplayName)
        // Fallback snapshot carries no resolved reference but still records the captured tab.
        assertTrue(loaded.first().sessionReference.isNullOrBlank())

        released.countDown()
        pool.shutdownNow()
        tempDir.deleteRecursively()
    }
}
