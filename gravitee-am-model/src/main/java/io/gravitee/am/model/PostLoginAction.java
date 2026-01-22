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

import io.gravitee.am.model.oidc.Client;

/**
 * Post Login Action configuration.
 * Allows redirecting to an external service after login for additional validation.
 *
 * @author GraviteeSource Team
 */
public class PostLoginAction {

    public static final long DEFAULT_ACTION_TIMEOUT = 60_000L;
    public static final String DEFAULT_RESPONSE_TOKEN_PARAM = "token";
    public static final String DEFAULT_SUCCESS_CLAIM = "status";
    public static final String DEFAULT_SUCCESS_VALUE = "approved";
    public static final String DEFAULT_ERROR_CLAIM = "error";
    public static final String DEFAULT_DATA_CLAIM = "data";

    /**
     * Enable/Disable post login action
     */
    private boolean enabled;

    /**
     * Post login action configuration inherited from domain?
     */
    private boolean inherited = true;

    /**
     * Certificate ID for signing the state JWT sent to external service
     */
    private String certificateId;

    /**
     * External service URL to redirect to after login
     */
    private String url;

    /**
     * Timeout in milliseconds for the external action
     */
    private long timeout = DEFAULT_ACTION_TIMEOUT;

    /**
     * PEM-encoded public key to validate the external service's JWT response
     */
    private String responsePublicKey;

    /**
     * Query parameter name for the JWT response token from external service
     */
    private String responseTokenParam = DEFAULT_RESPONSE_TOKEN_PARAM;

    /**
     * JWT claim name for success indicator in response
     */
    private String successClaim = DEFAULT_SUCCESS_CLAIM;

    /**
     * Expected value for success in the status claim
     */
    private String successValue = DEFAULT_SUCCESS_VALUE;

    /**
     * JWT claim name for error message in response
     */
    private String errorClaim = DEFAULT_ERROR_CLAIM;

    /**
     * JWT claim name for additional data in response
     */
    private String dataClaim = DEFAULT_DATA_CLAIM;

    public PostLoginAction() {
    }

    public PostLoginAction(PostLoginAction other) {
        this.enabled = other.enabled;
        this.inherited = other.inherited;
        this.certificateId = other.certificateId;
        this.url = other.url;
        this.timeout = other.timeout;
        this.responsePublicKey = other.responsePublicKey;
        this.responseTokenParam = other.responseTokenParam;
        this.successClaim = other.successClaim;
        this.successValue = other.successValue;
        this.errorClaim = other.errorClaim;
        this.dataClaim = other.dataClaim;
    }

    /**
     * Get the effective PostLoginAction settings considering inheritance.
     *
     * @param domain the domain
     * @param client the client (application)
     * @return the effective PostLoginAction settings
     */
    public static PostLoginAction getInstance(Domain domain, Client client) {
        // if client has no post login action config return domain config
        if (client == null || client.getPostLoginAction() == null) {
            return domain != null ? domain.getPostLoginAction() : null;
        }

        // if client configuration is not inherited return the client config
        if (!client.getPostLoginAction().isInherited()) {
            return client.getPostLoginAction();
        }

        // return domain config
        return domain != null ? domain.getPostLoginAction() : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    public String getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(String certificateId) {
        this.certificateId = certificateId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getResponsePublicKey() {
        return responsePublicKey;
    }

    public void setResponsePublicKey(String responsePublicKey) {
        this.responsePublicKey = responsePublicKey;
    }

    public String getResponseTokenParam() {
        return responseTokenParam;
    }

    public void setResponseTokenParam(String responseTokenParam) {
        this.responseTokenParam = responseTokenParam;
    }

    public String getSuccessClaim() {
        return successClaim;
    }

    public void setSuccessClaim(String successClaim) {
        this.successClaim = successClaim;
    }

    public String getSuccessValue() {
        return successValue;
    }

    public void setSuccessValue(String successValue) {
        this.successValue = successValue;
    }

    public String getErrorClaim() {
        return errorClaim;
    }

    public void setErrorClaim(String errorClaim) {
        this.errorClaim = errorClaim;
    }

    public String getDataClaim() {
        return dataClaim;
    }

    public void setDataClaim(String dataClaim) {
        this.dataClaim = dataClaim;
    }
}
