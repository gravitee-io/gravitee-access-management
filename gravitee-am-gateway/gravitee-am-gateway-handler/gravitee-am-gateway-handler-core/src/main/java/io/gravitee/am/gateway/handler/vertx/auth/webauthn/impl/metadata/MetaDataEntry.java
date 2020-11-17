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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.metadata;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Shareable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

// TODO to remove when updating to vert.x 4
public class MetaDataEntry implements Shareable {

    private static final Base64.Decoder BASE64DEC = Base64.getDecoder();
    private static final List<String> INVALID_STATUS = Arrays.asList("USER_VERIFICATION_BYPASS", "ATTESTATION_KEY_COMPROMISE", "USER_KEY_REMOTE_COMPROMISE", "USER_KEY_PHYSICAL_COMPROMISE", "REVOKED");

    private final JsonObject entry;
    private final JsonObject statement;
    private final String error;


    public MetaDataEntry(JsonObject statement) {
        if (statement == null) {
            throw new IllegalArgumentException("MetaData statement cannot be null");
        }
        this.entry = null;
        this.statement = statement;
        this.error = null;
    }

    public MetaDataEntry(JsonObject tocEntry, byte[] rawStatement, String error) throws NoSuchAlgorithmException {
        if (tocEntry == null || rawStatement == null) {
            throw new IllegalArgumentException("toc and statement cannot be null");
        }

        this.entry = tocEntry;
        this.statement = new JsonObject(Buffer.buffer(BASE64DEC.decode(rawStatement)));

        // convert status report effective date to a Instant
        for (Object o : entry.getJsonArray("statusReports")) {
            JsonObject statusReport = (JsonObject) o;
            statusReport.put(
                    "effectiveDate",
                    LocalDate.parse(statusReport.getString("effectiveDate"), DateTimeFormatter.ISO_DATE).atStartOfDay().toInstant(ZoneOffset.UTC));
        }

        if (error != null) {
            this.error = error;
        } else {
            // verify the hash
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(rawStatement);
            if (MessageDigest.isEqual(digest, entry.getBinary("hash"))) {
                this.error = null;
            } else {
                this.error = "MDS entry hash did not match corresponding hash in MDS TOC";
            }
        }
    }

    void checkValid() throws MetaDataException {

        if (error != null) {
            throw new MetaDataException(error);
        }

        if (entry != null) {
            final Instant now = Instant.now();
            // look up the status reports, backwards
            JsonArray reports = entry.getJsonArray("statusReports");

            for (int i = reports.size() - 1; i >= 0; i--) {
                JsonObject statusReport = reports.getJsonObject(i);
                if (statusReport.getInstant("effectiveDate").isBefore(now)) {
                    if (INVALID_STATUS.contains(statusReport.getString("status"))) {
                        throw new MetaDataException("Invalid MDS status: " + statusReport.getString("status"));
                    }
                    return;
                }
            }
            // no status was found for the current date
            throw new MetaDataException("Invalid MDS statusReports");
        }
    }

    JsonObject statement() {
        return statement;
    }
}
