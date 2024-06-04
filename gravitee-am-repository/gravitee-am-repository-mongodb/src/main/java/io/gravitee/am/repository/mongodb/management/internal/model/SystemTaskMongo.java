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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.SystemTask;
import io.gravitee.am.repository.mongodb.common.model.Auditable;
import org.bson.codecs.pojo.annotations.BsonId;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SystemTaskMongo extends Auditable {

    @BsonId
    private String id;
    private String type;
    private String kind;
    private String status;
    private String operationId;

    private String configuration;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public SystemTask convert() {
        SystemTask task = new SystemTask();
        task.setId(getId());
        task.setType(getType());
        task.setKind(getKind());
        task.setStatus(getStatus());
        task.setCreatedAt(getCreatedAt());
        task.setUpdatedAt(getUpdatedAt());
        task.setOperationId(getOperationId());
        task.setConfiguration(getConfiguration());
        return task;
    }

    public static SystemTaskMongo convert(SystemTask task) {
        if (task == null) {
            return null;
        }

        SystemTaskMongo taskMongo = new SystemTaskMongo();
        taskMongo.setId(task.getId());
        taskMongo.setType(task.getType());
        taskMongo.setKind(task.getKind());
        taskMongo.setStatus(task.getStatus());
        taskMongo.setCreatedAt(task.getCreatedAt());
        taskMongo.setUpdatedAt(task.getUpdatedAt());
        taskMongo.setOperationId(task.getOperationId());
        taskMongo.setConfiguration(task.getConfiguration());

        return taskMongo;
    }
}
