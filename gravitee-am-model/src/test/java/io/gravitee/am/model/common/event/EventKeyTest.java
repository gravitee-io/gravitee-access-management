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
package io.gravitee.am.model.common.event;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.*;

public class EventKeyTest {

    @Test
    public void defaultKeyTest (){
        Event event = new Event(Type.IDENTITY_PROVIDER, new Payload("id", new Reference(ReferenceType.DOMAIN, "domainId"), Action.CREATE));
        EventKey key = new EventKey(event);

        Assertions.assertEquals(Type.IDENTITY_PROVIDER, key.getType());
        Assertions.assertEquals("id", key.getKey());

    }

    @Test
    public void reporterKeyTestWithoutReference (){
        Event event = new Event(Type.REPORTER, new Payload("id", new Reference(ReferenceType.DOMAIN, "domainId"), Action.CREATE));
        EventKey key = new EventKey(event);

        Assertions.assertEquals(Type.REPORTER, key.getType());
        Assertions.assertEquals("id", key.getKey());

    }

    @Test
    public void reporterKeyTestWithReference (){
        Payload payload = new Payload("id", new Reference(ReferenceType.ORGANIZATION, "orgId"), Action.CREATE);
        payload.put("childReporterReference", new Payload("id2", new Reference(ReferenceType.DOMAIN, "domainId"), Action.UPDATE));
        Event event = new Event(Type.REPORTER, payload);
        EventKey key = new EventKey(event);

        Assertions.assertEquals(Type.REPORTER, key.getType());
        Assertions.assertEquals("id/id2", key.getKey());

    }

}