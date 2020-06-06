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
package io.gravitee.am.model.uma.policy.groovy;

import io.gravitee.am.model.uma.policy.AccessPolicyCondition;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroovyAccessPolicyCondition implements AccessPolicyCondition {

    private String onRequestScript;

    public GroovyAccessPolicyCondition() { }

    public GroovyAccessPolicyCondition(String onRequestScript) {
        this.onRequestScript = onRequestScript;
    }

    public String getOnRequestScript() {
        return onRequestScript;
    }

    public void setOnRequestScript(String onRequestScript) {
        this.onRequestScript = onRequestScript;
    }

    @Override
    public String toString() {
        return "{\"onRequestScript\":" + (onRequestScript == null ? "null" : "\"" + onRequestScript + "\"") + "}";
    }
}
