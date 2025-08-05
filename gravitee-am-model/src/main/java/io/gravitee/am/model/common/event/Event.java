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

import io.gravitee.am.common.event.Type;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@ToString
public class Event {

    private String id;
    private Type type;
    private Payload payload;
    private Date createdAt;
    private Date updatedAt;
    private String dataPlaneId;
    private String environmentId;

    public Event() { }

    public Event(Type type, Payload payload) {
        this.type = type;
        this.payload = payload;
    }
    public Event(Type type, Payload payload, String dataPlaneId, String environmentId) {
        this.type = type;
        this.payload = payload;
        this.dataPlaneId = dataPlaneId;
        this.environmentId = environmentId;
    }

}
