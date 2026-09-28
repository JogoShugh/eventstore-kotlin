package org.starbornag.eventstore.architecture

import assertk.assertThat
import assertk.assertions.containsAtLeast
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/** Hexagonal boundaries of the event store library, enforced as tests instead of prose. */
class ArchitectureBoundaryTest {

    private val productionFiles
        get() = Konsist.scopeFromProject().files.filter { it.path.contains("/eventstore/src/main/") }

    // Guards the other rules: an empty scope would make every assertFalse pass vacuously.
    @Test
    fun `the scope contains the production files`() {
        assertThat(productionFiles.map { it.name }).containsAtLeast("EventStore", "PgEventStore", "EventSerializer")
    }

    @Test
    fun `the library never depends on Spring`() {
        productionFiles.assertFalse { file -> file.hasImport { it.name.startsWith("org.springframework") } }
    }

    @Test
    fun `the EventStore port imports no database or JSON library`() {
        productionFiles
            .filter { it.name == "EventStore" }
            .assertFalse { file ->
                file.hasImport { import ->
                    listOf("io.r2dbc", "org.postgresql", "com.fasterxml").any { import.name.startsWith(it) }
                }
            }
    }

    @Test
    fun `only the Postgres adapter uses the PostgreSQL driver`() {
        productionFiles
            .filter { it.name != "PgEventStore" }
            .assertFalse { file -> file.hasImport { it.name.startsWith("io.r2dbc.postgresql") } }
    }

    @Test
    fun `only the serializer uses Jackson`() {
        productionFiles
            .filter { it.name != "EventSerializer" }
            .assertFalse { file -> file.hasImport { it.name.startsWith("com.fasterxml") } }
    }
}
