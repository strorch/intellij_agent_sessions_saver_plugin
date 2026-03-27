package tests.unit.restore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import plugin.restore.ProjectScopeGuard

class ProjectScopeMappingTest {
    @Test
    fun `filters snapshots by project scope`() {
        val snapshots = listOf(
            RestoreFixtureFactory.snapshot(terminalTabId = "a", projectScopeId = "project-a"),
            RestoreFixtureFactory.snapshot(terminalTabId = "b", projectScopeId = "project-b"),
        )

        val result = ProjectScopeGuard().filterByProject(snapshots, "project-a")

        assertEquals(1, result.size)
        assertEquals("a", result.first().terminalTabId)
    }
}
