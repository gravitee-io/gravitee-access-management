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
/*
 * Copyright 2019 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.attestation;

import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthnOptions;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.AuthData;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.metadata.MetaData;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.impl.attestation.AttestationException;

/**
 * Implementation of the "none" attestation check.
 *
 * This is the most common kind of attestation. User Agents will recommend
 * users to use this, for privacy reasons. Most applications should use it
 * too, as trust should be build on first contact, not on the full hardware
 * check.
 *
 * @author <a href="mailto:pmlopes@gmail.com>Paulo Lopes</a>
 */
// TODO to remove when updating to vert.x 4
public class NoneAttestation implements Attestation {

    @Override
    public String fmt() {
        return "none";
    }

    @Override
    public void validate(WebAuthnOptions options, MetaData metadata, byte[] clientDataJSON, JsonObject attestation, AuthData authData) throws AttestationException {
        // AAGUID must be null
      /*  if (!"00000000-0000-0000-0000-000000000000".equals(authData.getAaguidString())) {
            throw new AttestationException("AAGUID is not 00000000-0000-0000-0000-000000000000!");
        }*/

        // attStmt must be empty
        if (attestation.containsKey("attStmt") && attestation.getJsonObject("attStmt").size() > 0) {
            throw new AttestationException("attStmt is present!");
        }
    }
}
