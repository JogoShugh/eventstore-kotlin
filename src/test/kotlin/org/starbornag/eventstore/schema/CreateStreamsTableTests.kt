package org.starbornag.eventstore.schema

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import org.starbornag.eventstore.tools.PostgresSchemaProvider.ColumnType
import org.starbornag.eventstore.tools.PostgresTest
import org.starbornag.eventstore.tools.test

class CreateStreamsTableTests : PostgresTest() {
    private val streamsTableName = "streams"

    @Test
    fun `streams table should be created`() = test {
        val streamsTable = schemaProvider.getTable(streamsTableName)

        assertThat(streamsTable).isNotNull()
        assertThat(streamsTable!!.name).isEqualTo(streamsTableName)
    }

    @Test
    fun `streams table should have id column`() = assertColumn("id", ColumnType.UUID)

    @Test
    fun `streams table should have type column with string type`() = assertColumn("type", ColumnType.TEXT)

    @Test
    fun `streams table should have version column with long type`() = assertColumn("version", ColumnType.BIGINT)

    private fun assertColumn(columnName: String, columnType: String) = test {
        val column = schemaProvider.getTable(streamsTableName)?.column(columnName)

        assertThat(column).isNotNull()
        assertThat(column!!.type).isEqualTo(columnType)
    }
}
