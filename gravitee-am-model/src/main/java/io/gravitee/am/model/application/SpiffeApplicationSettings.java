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
package io.gravitee.am.model.application;

/**
 * Per-application SPIFFE settings, populated when the application uses the
 * {@code spiffe_jwt} client-authentication method.
 */
public class SpiffeApplicationSettings {

    /**
     * Name of the {@code TrustDomain} this application authenticates against.
     */
    private String trustDomain;

    /**
     * Exact SPIFFE ID expected in the SVID's {@code sub} claim. Mutually exclusive with {@link #subjectPattern}.
     */
    private String subject;

    /**
     * Glob pattern matched against the SVID's {@code sub} claim. {@code *} matches one segment, {@code **} matches any.
     */
    private String subjectPattern;

    public SpiffeApplicationSettings() {
    }

    public SpiffeApplicationSettings(SpiffeApplicationSettings other) {
        this.trustDomain = other.trustDomain;
        this.subject = other.subject;
        this.subjectPattern = other.subjectPattern;
    }

    public String getTrustDomain() {
        return trustDomain;
    }

    public void setTrustDomain(String trustDomain) {
        this.trustDomain = trustDomain;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSubjectPattern() {
        return subjectPattern;
    }

    public void setSubjectPattern(String subjectPattern) {
        this.subjectPattern = subjectPattern;
    }
}
