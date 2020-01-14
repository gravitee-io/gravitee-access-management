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
package io.gravitee.am.reporter.api.audit;

import io.gravitee.am.reporter.api.provider.ReportableCriteria;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuditReportableCriteria implements ReportableCriteria {

    private long from;
    private long to;
    private long interval;
    private Integer size;
    private List<String> types;
    private String field;
    private String status;
    private String user;

    private AuditReportableCriteria(Builder builder) {
        this.from = builder.from;
        this.to = builder.to;
        this.interval = builder.interval;
        this.size = builder.size;
        this.types = builder.types;
        this.field = builder.field;
        this.status = builder.status;
        this.user = builder.user;
    }

    @Override
    public long from() {
        return from;
    }

    @Override
    public long to() {
        return to;
    }

    public long interval() {
        return interval;
    }

    public Integer size() {
        return size;
    }

    public List<String> types() {
        return types;
    }

    public String field() {
        return field;
    }

    public String status() {
        return status;
    }

    public String user() {
        return user;
    }

    public static class Builder {
        private long from;
        private long to;
        private long interval;
        private Integer size;
        private List<String> types;
        private String field;
        private String status;
        private String user;

        public Builder from(long from) {
            this.from = from;
            return this;
        }

        public Builder to(long to) {
            this.to = to;
            return this;
        }

        public Builder interval(long interval) {
            this.interval = interval;
            return this;
        }

        public Builder size(Integer size) {
            this.size = size;
            return this;
        }

        public Builder types(List<String> types) {
            this.types = types;
            return this;
        }

        public Builder field(String field) {
            this.field = field;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public AuditReportableCriteria build() {
            return new AuditReportableCriteria(this);
        }
    }
}
