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
package io.gravitee.am.common.email;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Setter
@Getter
public class Email {

    private String from;
    private String fromName;
    private String[] to;
    private String[] bcc;
    private String subject;
    private String template;
    private String content;
    private Map<String, Object> params = new HashMap<>();

    public Email() {}

    public Email(Email other) {
        this.from = other.from;
        this.fromName = other.fromName;
        this.to = other.to;
        this.bcc = other.bcc;
        this.subject = other.subject;
        this.template = other.template;
        this.content = other.content;
        this.params = other.params != null ? new HashMap<>(other.params) : null;
    }
}
