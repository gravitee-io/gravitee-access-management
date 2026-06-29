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
package io.gravitee.am.management.handlers.management.api.resources.utils;

import io.gravitee.am.management.handlers.management.api.model.FlowEntity;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.model.flow.Type;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class FlowUtilsTest {

    @Test
    public void convert_shouldCopyEveryField() {
        Step pre = new Step();
        pre.setName("pre-step");
        Step post = new Step();
        post.setName("post-step");

        io.gravitee.am.service.model.Flow source = new io.gravitee.am.service.model.Flow();
        source.setId("flow-1");
        source.setType(Type.TOKEN);
        source.setName("TOKEN");
        source.setEnabled(true);
        source.setCondition("{#context.attributes['x'] == 'y'}");
        source.setPre(List.of(pre));
        source.setPost(List.of(post));

        List<io.gravitee.am.model.flow.Flow> converted = FlowUtils.convert(List.of(source));

        assertEquals(1, converted.size());
        io.gravitee.am.model.flow.Flow result = converted.getFirst();
        assertEquals("flow-1", result.getId());
        assertEquals(Type.TOKEN, result.getType());
        assertEquals("TOKEN", result.getName());
        assertTrue(result.isEnabled());
        assertEquals("{#context.attributes['x'] == 'y'}", result.getCondition());
        assertSame(source.getPre(), result.getPre());
        assertSame(source.getPost(), result.getPost());
    }

    @Test
    public void convert_shouldHandleNullCollectionsAndDefaults() {
        io.gravitee.am.service.model.Flow source = new io.gravitee.am.service.model.Flow();

        List<io.gravitee.am.model.flow.Flow> converted = FlowUtils.convert(List.of(source));

        assertEquals(1, converted.size());
        io.gravitee.am.model.flow.Flow result = converted.getFirst();
        assertNull(result.getId());
        assertNull(result.getName());
        assertNull(result.getCondition());
        assertNull(result.getPre());
        assertNull(result.getPost());
        assertEquals(source.isEnabled(), result.isEnabled());
    }

    @Test
    public void convert_shouldReturnEmptyListForEmptyInput() {
        assertTrue(FlowUtils.convert(List.of()).isEmpty());
    }

    @Test
    public void filterFlowInfos_shouldReturnFullEntityWhenPermissionGranted() {
        Step pre = new Step();
        pre.setName("pre-step");

        io.gravitee.am.model.flow.Flow flow = new io.gravitee.am.model.flow.Flow();
        flow.setId("flow-1");
        flow.setName("TOKEN");
        flow.setEnabled(true);
        flow.setType(Type.TOKEN);
        flow.setCondition("condition");
        flow.setPre(List.of(pre));

        FlowEntity entity = FlowUtils.filterFlowInfos(true, flow);

        assertEquals("flow-1", entity.getId());
        assertEquals("TOKEN", entity.getName());
        assertTrue(entity.isEnabled());
        assertEquals(Type.TOKEN, entity.getType());
        assertEquals("condition", entity.getCondition());
        assertSame(flow.getPre(), entity.getPre());
    }

    @Test
    public void filterFlowInfos_shouldStripDetailsWhenPermissionDenied() {
        Step pre = new Step();
        pre.setName("pre-step");

        io.gravitee.am.model.flow.Flow flow = new io.gravitee.am.model.flow.Flow();
        flow.setId("flow-1");
        flow.setName("TOKEN");
        flow.setEnabled(true);
        flow.setType(Type.TOKEN);
        flow.setCondition("condition");
        flow.setPre(List.of(pre));

        FlowEntity entity = FlowUtils.filterFlowInfos(false, flow);

        assertEquals("flow-1", entity.getId());
        assertEquals("TOKEN", entity.getName());
        assertTrue(entity.isEnabled());
        assertNull(entity.getType());
        assertNull(entity.getCondition());
        assertNull(entity.getPre());
        assertNull(entity.getPost());
    }
}
