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
package io.gravitee.am.management.handlers.automation.model;

import io.gravitee.am.common.saml2.Binding;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Automation API mirror of {@link io.gravitee.am.model.application.ApplicationSAMLSettings}: the application
 * acting as a SAML 2.0 service provider (SP) of the domain.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationApplicationSAMLSettings", title = "Application SAML 2.0 settings",
        description = "Settings of the application acting as a SAML 2.0 service provider (SP).")
public class AutomationApplicationSAMLSettings {

    @Schema(description = "URL or URN that uniquely identifies the SP (the SAML entity ID).",
            example = "https://app.example.com")
    private String entityId;

    @Schema(description = "SP endpoint the IdP sends SAML responses to (Assertion Consumer Service URL).",
            example = "https://app.example.com/saml/acs")
    private String attributeConsumeServiceUrl;

    @Schema(description = "SP endpoint the IdP redirects to after a single logout.",
            example = "https://app.example.com/saml/logout")
    private String singleLogoutServiceUrl;

    @Schema(description = "The SP's PEM-encoded public key certificate, used by the IdP to verify SAML requests and, " +
            "when assertions are encrypted, to wrap the symmetric key. Required when wantAssertionsEncrypted is true.")
    private String certificate;

    @Schema(description = "Whether the SP requires signed SAML responses.", defaultValue = "true")
    private boolean wantResponseSigned = true;

    @Schema(description = "Whether the SP requires signed SAML assertions.", defaultValue = "false")
    private boolean wantAssertionsSigned;

    @Schema(description = "Whether the SP requires encrypted SAML assertions.", defaultValue = "false")
    private boolean wantAssertionsEncrypted;

    @Schema(description = "Key transport algorithm URI of encrypted assertions (XML Encryption EncryptedKey).",
            example = "http://www.w3.org/2009/xmlenc11#rsa-oaep")
    private String keyTransportEncryptionAlgorithm;

    @Schema(description = "Data encryption algorithm URI of encrypted assertions (XML Encryption EncryptedData).",
            example = "http://www.w3.org/2009/xmlenc11#aes256-gcm")
    private String dataEncryptionAlgorithm;

    @Schema(description = "Binding used to send SAML responses to the SP. The default answers with the binding of " +
            "the initial request.", defaultValue = "urn:oasis:names:tc:SAML:2.0:bindings:custom:Initial-Request")
    private String responseBinding = Binding.INITIAL_REQUEST;

    @Schema(description = "Expression Language expression producing the NameID of the SAML response. When empty, " +
            "the internal user id is used, or the user's email when the SP requests the email format.",
            example = "{#context.attributes['user'].username}")
    private String nameIdMapping;

    @Schema(description = "Custom attributes of the SAML assertion. When not empty, they replace the default " +
            "attribute set.")
    private List<AutomationSamlAssertionAttribute> assertionAttributes;

    @Schema(description = "Whether assertions carry Conditions (validity time bounds and audience restriction), " +
            "configured by the fields below.", defaultValue = "false")
    private boolean includeAssertionConditions;

    @Schema(description = "Audience URIs added to the SP entity ID in the AudienceRestriction. Used when " +
            "includeAssertionConditions is true.")
    private List<String> audiences;

    @Schema(description = "Validity, in seconds, of the issued assertion. When absent, the IdP default applies. " +
            "Used when includeAssertionConditions is true.", example = "300")
    private Integer assertionValiditySeconds;

    @Schema(description = "Clock skew, in seconds, subtracted from NotBefore.", example = "30")
    private Integer notBeforeTimeSkewSeconds;

    @Schema(description = "Clock skew, in seconds, added to NotOnOrAfter.", example = "30")
    private Integer notOnOrAfterTimeSkewSeconds;
}
