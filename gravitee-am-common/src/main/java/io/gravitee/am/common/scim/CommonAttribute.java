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
package io.gravitee.am.common.scim;

/**
 * See <a href="https://tools.ietf.org/html/rfc7643#section-3.1">3.1.  Common Attributes</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CommonAttribute {

    /**
     * A unique identifier for a SCIM resource as defined by the service provider.
     */
    String ID = "id";
    /**
     * A String that is an identifier for the resource as defined by the provisioning client.
     */
    String EXTERNAL_ID= "externalId";
    /**
     * A complex attribute containing resource metadata.
     */
    String META = "meta";
}
