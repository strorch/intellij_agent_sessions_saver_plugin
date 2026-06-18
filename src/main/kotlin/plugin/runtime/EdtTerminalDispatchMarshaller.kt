package plugin.runtime

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import plugin.restore.TerminalDispatchMarshaller
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production [TerminalDispatchMarshaller] that runs the terminal-widget dispatch on the EDT.
 *
 * Restore attempts run on a worker thread (so the per-attempt timeout is enforceable), but the
 * dispatch body touches IntelliJ terminal widget APIs that require the EDT. This marshaller posts
 * the dispatch to the EDT via [com.intellij.openapi.application.Application.invokeAndWait] and
 * blocks the calling worker thread until it completes. The worker — not the EDT — is the thread
 * that blocks, so a hung dispatch is still subject to the worker-side [java.util.concurrent.Future]
 * timeout, while widget APIs only ever execute on the EDT.
 *
 * If the calling thread already is the EDT, the dispatch runs inline to avoid re-entrant
 * scheduling.
 */
class EdtTerminalDispatchMarshaller : TerminalDispatchMarshaller {
    override fun dispatchOnUiThread(dispatch: () -> Boolean): Boolean {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            return dispatch()
        }
        val result = AtomicBoolean(false)
        // invokeAndWait blocks this (worker) thread until the EDT has run the dispatch. It must not
        // be called from the EDT (handled above) to avoid deadlock.
        app.invokeAndWait({ result.set(dispatch()) }, ModalityState.nonModal())
        return result.get()
    }
}
