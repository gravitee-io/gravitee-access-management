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
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Payload extends HashMap<String, Object> {

    private static final String ID = "id";
    private static final String REFERENCE_TYPE = "referenceType";
    private static final String REFERENCE_ID = "referenceId";
    private static final String ACTION = "action";

    public Payload(String id, Reference reference, Action action) {
        this(id, reference.type(), reference.id(), action);
    }

    public Payload(String id, ReferenceType referenceType, String referenceId, Action action) {
        put(ID, id);
        if(referenceType != null) {
            put(REFERENCE_TYPE, referenceType.name());
        }
        put(REFERENCE_ID, referenceId);
        put(ACTION, action);
    }

    public Payload(Map<? extends String, ?> m) {
        super(m);
    }

    public String getId() {
        return (String) get(ID);
    }

    public ReferenceType getReferenceType() {
        String rawReferenceType = (String) get(REFERENCE_TYPE);
        if (rawReferenceType == null) {
            return null;
        } else {
            return ReferenceType.valueOf(rawReferenceType);
        }
    }

    public String getReferenceId() {
        return (String) get(REFERENCE_ID);
    }

    public Action getAction() {
        return (Action) get(ACTION);
    }
}
