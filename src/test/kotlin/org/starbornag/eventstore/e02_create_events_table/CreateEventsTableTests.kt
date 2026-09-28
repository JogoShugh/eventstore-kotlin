package org.starbornag.eventstore.e02_create_events_table

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import org.starbornag.eventstore.tools.PostgresSchemaProvider.ColumnType
import org.starbornag.eventstore.tools.PostgresTest
import org.starbornag.eventstore.tools.test

class CreateEventsTableTests : PostgresTest() {
    private val eventsTableName = "events"

    @Test
    fun `events table should be created`() = test {
        val eventsTable = schemaProvider.getTable(eventsTableName)

        assertThat(eventsTable).isNotNull()
        assertThat(eventsTable!!.name).isEqualTo(eventsTableName)
    }

    @Test
    fun `events table should have id column`() = assertColumn("id", ColumnType.UUID)

    @Test
    fun `events table should have stream id column`() = assertColumn("stream_id", ColumnType.UUID)

    @Test
    fun `events table should have data column`() = assertColumn("data", ColumnType.JSONB)

    @Test
    fun `events table should have type column with string type`() = assertColumn("type", ColumnType.TEXT)

    @Test
    fun `events table should have version column with long type`() = assertColumn("version", ColumnType.BIGINT)

    @Test
    fun `events table should have created column with timestamp type`() =
        assertColumn("created", ColumnType.TIMESTAMP_WITH_TIME_ZONE)

    private fun assertColumn(columnName: String, columnType: String) = test {
        val column = schemaProvider.getTable(eventsTableName)?.column(columnName)

        assertThat(column).isNotNull()
        assertThat(column!!.type).isEqualTo(columnType)
    }
}
