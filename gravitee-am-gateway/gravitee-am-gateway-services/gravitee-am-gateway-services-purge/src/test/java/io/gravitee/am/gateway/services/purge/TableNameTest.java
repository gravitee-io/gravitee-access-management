/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.services.purge;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TableNameTest {

    @Test
    public void shouldIncludeEventsTable() {
        // Given: Get all table names
        TableName[] allTables = TableName.values();
        List<String> tableNames = Arrays.stream(allTables).map(TableName::name).toList();

        // Then: Verify events table is included
        assertTrue(tableNames.contains("events"), "Events table should be included in TableName.values()");
        
        // Log all tables for debugging
        System.out.println("All available tables: " + tableNames);
        System.out.println("Total number of tables: " + allTables.length);
    }

    @Test
    public void shouldFindEventsTableByName() {
        // Given: Look for events table by name
        var eventsTable = TableName.getValueOf("events");

        // Then: Should find the events table
        assertTrue(eventsTable.isPresent(), "Should find events table by name");
        assertEquals(TableName.events, eventsTable.get(), "Should return correct events table enum");
    }

    @Test
    public void shouldNotFindNonExistentTable() {
        // Given: Look for non-existent table
        var nonExistentTable = TableName.getValueOf("non_existent_table");

        // Then: Should not find the table
        assertFalse(nonExistentTable.isPresent(), "Should not find non-existent table");
    }
} 