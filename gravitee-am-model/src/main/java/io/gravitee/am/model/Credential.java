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
package io.gravitee.am.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * WebAuthn Credential / Authenticator record
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Credential {

    private String id;
    private ReferenceType referenceType;
    private String referenceId;
    private String userId;
    private String username;
    private String credentialId;
    private String publicKey;
    private Long counter;
    private String aaguid;
    private String attestationStatementFormat;
    private String attestationStatement;
    private String ipAddress;
    private String userAgent;
    private String deviceName;
    @Schema(type = "java.lang.Long")
    private Date createdAt;
    @Schema(type = "java.lang.Long")
    private Date updatedAt;
    @Schema(type = "java.lang.Long")
    private Date accessedAt;
    @Schema(type = "java.lang.Long")
    private Date lastCheckedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public Long getCounter() {
        return counter;
    }

    public void setCounter(Long counter) {
        this.counter = counter;
    }

    public String getAaguid() {
        return aaguid;
    }

    public void setAaguid(String aaguid) {
        this.aaguid = aaguid;
    }

    public String getAttestationStatementFormat() {
        return attestationStatementFormat;
    }

    public void setAttestationStatementFormat(String attestationStatementFormat) {
        this.attestationStatementFormat = attestationStatementFormat;
    }

    public String getAttestationStatement() {
        return attestationStatement;
    }

    public void setAttestationStatement(String attestationStatement) {
        this.attestationStatement = attestationStatement;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Date getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(Date accessedAt) {
        this.accessedAt = accessedAt;
    }

    public Date getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Date lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }
}
