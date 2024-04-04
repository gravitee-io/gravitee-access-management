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
package io.gravitee.am.common.saml2;

/**
 * SAML protocol bindings for the use of SAML assertions and request-response
 * messages in communications protocols and frameworks.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Binding {

    /**
     * The initial SAML binding of the original request
     */
    String INITIAL_REQUEST = "urn:oasis:names:tc:SAML:2.0:bindings:custom:Initial-Request";

    /**
     * The HTTP POST binding defines a mechanism by which SAML protocol messages may be transmitted
     * within the base64-encoded content of an HTML form control.
     */
    String HTTP_POST = "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST";

    /**
     * The HTTP Redirect binding is intended for cases in which the SAML requester and responder need to
     * communicate using an HTTP user agent.
     */
    String HTTP_REDIRECT = "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect";
}
