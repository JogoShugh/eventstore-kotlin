package org.starbornag.eventstore.schema

import assertk.assertThat
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import org.starbornag.eventstore.tools.PostgresTest
import org.starbornag.eventstore.tools.test

class AppendEventFunctionTests : PostgresTest() {

    @Test
    fun `append_event function should be created`() = test {
        assertThat(schemaProvider.functionExists("append_event")).isTrue()
    }
}
