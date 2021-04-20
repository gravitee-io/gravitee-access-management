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
package io.gravitee.am.identityprovider.api;

import io.gravitee.gateway.api.ExecutionContext;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AuthenticationContext extends ExecutionContext {
    /**
     * Stores an attribute in this context.
     *
     * @param name a String specifying the name of the attribute
     * @param value the Object to be stored
     */
    default AuthenticationContext set(String name, Object value) {
        setAttribute(name, value);
        return this;
    }

    /**
     * Removes an attribute from this context.
     * long as the request is being handled.
     *
     * @param name a String specifying the name of the attribute to remove
     */
    default AuthenticationContext remove(String name) {
        removeAttribute(name);
        return this;
    }

    /**
     * Returns the value of the named attribute as an Object, or <code>null</code> if no attribute of the given
     * name exists.
     *
     * @param name a String specifying the name of the attribute
     * @return an Object containing the value of the attribute, or null if the attribute does not exist
     */
    default Object get(String name) {
        return getAttribute(name);
    }

    default Map<String, Object> attributes() {
        return getAttributes();
    }
}
