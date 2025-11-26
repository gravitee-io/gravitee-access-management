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
package io.gravitee.am.gateway.handler.common.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author GraviteeSource Team
 */
public class MapUtilsTest {

    @Test
    void shouldReturnEmptyWhenMapIsNull() {
        Optional<List<String>> result = MapUtils.extractStringList(null, "key");
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenKeyDoesNotExist() {
        Map<String, Object> map = new HashMap<>();
        map.put("otherKey", "value");

        Optional<List<String>> result = MapUtils.extractStringList(map, "nonExistentKey");
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenValueIsNull() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", null);

        Optional<List<String>> result = MapUtils.extractStringList(map, "key");
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenValueIsNotAList() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "not a list");

        Optional<List<String>> result = MapUtils.extractStringList(map, "key");
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenListContainsNonStringElements() {
        Map<String, Object> map = new HashMap<>();
        List<Object> mixedList = new ArrayList<>();
        mixedList.add("string1");
        mixedList.add(123);
        mixedList.add("string2");
        map.put("key", mixedList);

        Optional<List<String>> result = MapUtils.extractStringList(map, "key");
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenListIsEmpty() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", new ArrayList<String>());

        Optional<List<String>> result = MapUtils.extractStringList(map, "key");
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    void shouldExtractValidStringList() {
        Map<String, Object> map = new HashMap<>();
        List<String> stringList = new ArrayList<>();
        stringList.add("value1");
        stringList.add("value2");
        stringList.add("value3");
        map.put("key", stringList);

        Optional<List<String>> result = MapUtils.extractStringList(map, "key");
        assertTrue(result.isPresent());
        List<String> extracted = result.get();
        assertEquals(3, extracted.size());
        assertEquals("value1", extracted.get(0));
        assertEquals("value2", extracted.get(1));
        assertEquals("value3", extracted.get(2));
    }

    @Test
    void shouldExtractValidStringListWithSingleElement() {
        Map<String, Object> map = new HashMap<>();
        List<String> stringList = new ArrayList<>();
        stringList.add("singleValue");
        map.put("key", stringList);

        Optional<List<String>> result = MapUtils.extractStringList(map, "key");
        assertTrue(result.isPresent());
        List<String> extracted = result.get();
        assertEquals(1, extracted.size());
        assertEquals("singleValue", extracted.get(0));
    }

    @Test
    void shouldExtractValidStringListWithEmptyStrings() {
        Map<String, Object> map = new HashMap<>();
        List<String> stringList = new ArrayList<>();
        stringList.add("");
        stringList.add("nonEmpty");
        stringList.add("");
        map.put("key", stringList);

        Optional<List<String>> result = MapUtils.extractStringList(map, "key");
        assertTrue(result.isPresent());
        List<String> extracted = result.get();
        assertEquals(3, extracted.size());
        assertEquals("", extracted.get(0));
        assertEquals("nonEmpty", extracted.get(1));
        assertEquals("", extracted.get(2));
    }

    @Test
    void shouldReturnEmptyWhenListContainsNullElements() {
        Map<String, Object> map = new HashMap<>();
        List<Object> listWithNull = new ArrayList<>();
        listWithNull.add("string1");
        listWithNull.add(null);
        listWithNull.add("string2");
        map.put("key", listWithNull);

        Optional<List<String>> result = MapUtils.extractStringList(map, "key");
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenListContainsOnlyNonStringElements() {
        Map<String, Object> map = new HashMap<>();
        List<Object> nonStringList = new ArrayList<>();
        nonStringList.add(123);
        nonStringList.add(456);
        map.put("key", nonStringList);

        Optional<List<String>> result = MapUtils.extractStringList(map, "key");
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenListContainsBooleanElements() {
        Map<String, Object> map = new HashMap<>();
        List<Object> booleanList = new ArrayList<>();
        booleanList.add(true);
        booleanList.add(false);
        map.put("key", booleanList);

        Optional<List<String>> result = MapUtils.extractStringList(map, "key");
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenListContainsMapElements() {
        Map<String, Object> map = new HashMap<>();
        List<Object> mapList = new ArrayList<>();
        mapList.add(new HashMap<>());
        map.put("key", mapList);

        Optional<List<String>> result = MapUtils.extractStringList(map, "key");
        assertFalse(result.isPresent());
    }
}

