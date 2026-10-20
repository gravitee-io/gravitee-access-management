/*
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
import type { TrustedIssuer } from './TrustedIssuer';
import type { IdJagSettings } from './IdJagSettings';
import type { TokenExchangeOAuthSettings } from './TokenExchangeOAuthSettings';
/**
 * OAuth 2.0 Token Exchange (RFC 8693) configuration for the domain, covering impersonation and delegation.
 * @export
 * @interface TokenExchangeSettings
 */
export interface TokenExchangeSettings {
    /**
     * Whether delegation is allowed, where an actor acts on behalf of the subject and an "act" claim is added to the issued token. At least one of allowImpersonation or allowDelegation must be enabled.
     * @type {boolean}
     * @memberof TokenExchangeSettings
     */
    allowDelegation?: boolean;
    /**
     * Whether impersonation is allowed, where the issued token represents the subject directly. At least one of allowImpersonation or allowDelegation must be enabled.
     * @type {boolean}
     * @memberof TokenExchangeSettings
     */
    allowImpersonation?: boolean;
    /**
     * Token types accepted as the actor token when delegating.
     * @type {Array<string>}
     * @memberof TokenExchangeSettings
     */
    allowedActorTokenTypes?: Array<string>;
    /**
     * Token types that may be requested as the result of an exchange.
     * @type {Array<string>}
     * @memberof TokenExchangeSettings
     */
    allowedRequestedTokenTypes?: Array<string>;
    /**
     * Token types accepted as the subject token in an exchange.
     * @type {Array<string>}
     * @memberof TokenExchangeSettings
     */
    allowedSubjectTokenTypes?: Array<string>;
    /**
     * Whether token exchange is enabled for the domain.
     * @type {boolean}
     * @memberof TokenExchangeSettings
     */
    enabled?: boolean;
    /**
     *
     * @type {IdJagSettings}
     * @memberof TokenExchangeSettings
     */
    idJagSettings?: IdJagSettings;
    /**
     * Maximum depth of the delegation chain (nested "act" claims). Clamped to the range 1–100.
     * @type {number}
     * @memberof TokenExchangeSettings
     */
    maxDelegationDepth?: number;
    /**
     *
     * @type {TokenExchangeOAuthSettings}
     * @memberof TokenExchangeSettings
     */
    tokenExchangeOAuthSettings?: TokenExchangeOAuthSettings;
    /**
     * Deprecated: use the trusted-domains API instead. External issuers whose JWTs may be accepted as subject or actor tokens. A projection over the security domain's token-exchange trusted domains; a write replaces the list, so an omitted issuer is no longer trusted.
     * @type {Array<TrustedIssuer>}
     * @memberof TokenExchangeSettings
     * @deprecated
     */
    trustedIssuers?: Array<TrustedIssuer>;
}
/**
 * Check if a given object implements the TokenExchangeSettings interface.
 */
export declare function instanceOfTokenExchangeSettings(value: object): value is TokenExchangeSettings;
export declare function TokenExchangeSettingsFromJSON(json: any): TokenExchangeSettings;
export declare function TokenExchangeSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): TokenExchangeSettings;
export declare function TokenExchangeSettingsToJSON(json: any): TokenExchangeSettings;
export declare function TokenExchangeSettingsToJSONTyped(value?: TokenExchangeSettings | null, ignoreDiscriminator?: boolean): any;
