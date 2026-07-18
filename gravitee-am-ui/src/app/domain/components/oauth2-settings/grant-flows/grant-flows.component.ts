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
import { Component, EventEmitter, Inject, Input, OnInit, Output } from '@angular/core';
import { some, minBy } from 'lodash';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { DomainStoreService } from '../../../../stores/domain.store';
import { TrustDomainService } from '../../../../services/trust-domain.service';
import {
  TokenExchangeScopeHandling,
  TOKEN_EXCHANGE_SCOPE_HANDLING_OPTIONS,
  DEFAULT_TOKEN_EXCHANGE_SCOPE_HANDLING,
} from '../../../settings/oauth/token-exchange/token-exchange.types';

@Component({
  selector: 'app-grant-flows-settings',
  templateUrl: './grant-flows.component.html',
  styleUrls: ['./grant-flows.component.scss'],
  standalone: false,
})
export class GrantFlowsComponent implements OnInit {
  @Input() oauthSettings: any; // OAuth settings input
  @Input() domainId: string; // Domain ID from parent
  @Input() context: 'Application' | 'McpServer' = 'Application'; // context to toggle features (use MCP_SERVER_CONTEXT for McpServer)

  // Optional: Full application object if needed for specific logic (like tokenEndpointAuthMethods filtering)
  // Or better, pass specifically what is needed.
  @Input() secretSettings: any[] = [];
  @Input() applicationType: string = 'service'; // Default to service if not provided? Or make optional
  @Input() applicationKind: string | null = null; // For AGENT applications: HOSTED_DELEGATED | AUTONOMOUS | USER_EMBEDDED

  @Input() customGrantTypes: any[] = [];
  @Input() readonly = false;

  @Input() spiffeSettings: any = {};

  @Output() settingsChange = new EventEmitter<any>();
  @Output() spiffeSettingsChange = new EventEmitter<any>();
  @Output() formChanged = new EventEmitter<boolean>();

  trustDomains: any[] = [];

  readonly MCP_SERVER_CONTEXT = 'McpServer' as const;
  readonly CLIENT_CREDENTIALS_GRANT_TYPE = 'client_credentials';

  readonly DEFAULT_TOKEN_EXCHANGE_SCOPE_HANDLING = DEFAULT_TOKEN_EXCHANGE_SCOPE_HANDLING;
  readonly TOKEN_EXCHANGE_SCOPE_HANDLING_OPTIONS = TOKEN_EXCHANGE_SCOPE_HANDLING_OPTIONS;

  private CIBA_GRANT_TYPE = 'urn:openid:params:grant-type:ciba';
  private TOKEN_EXCHANGE_GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:token-exchange';
  private UMA_GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:uma-ticket';

  tokenEndpointAuthMethods: any[] = [
    { name: 'client_secret_basic', value: 'client_secret_basic' },
    { name: 'client_secret_post', value: 'client_secret_post' },
    { name: 'client_secret_jwt', value: 'client_secret_jwt' },
    { name: 'private_key_jwt', value: 'private_key_jwt' },
    { name: 'Mutual TLS - PKI Mutual (tls_client_auth)', value: 'tls_client_auth' },
    { name: 'Mutual TLS - Self-Signed Certificate Mutual (self_signed_tls_client_auth)', value: 'self_signed_tls_client_auth' },
    { name: 'spiffe_jwt', value: 'spiffe_jwt' },
    { name: 'none', value: 'none' },
  ];

  grantTypes: any[] = [
    { name: 'AUTHORIZATION CODE', value: 'authorization_code', checked: false, disabled: false },
    { name: 'IMPLICIT', value: 'implicit', checked: false, disabled: false },
    { name: 'REFRESH TOKEN', value: 'refresh_token', checked: false, disabled: false },
    { name: 'PASSWORD', value: 'password', checked: false },
    { name: 'CLIENT CREDENTIALS', value: this.CLIENT_CREDENTIALS_GRANT_TYPE, checked: false, disabled: false },
    { name: 'UMA TICKET', value: this.UMA_GRANT_TYPE, checked: false, disabled: false },
    { name: 'CIBA', value: this.CIBA_GRANT_TYPE, checked: false, disabled: false },
    { name: 'TOKEN EXCHANGE', value: this.TOKEN_EXCHANGE_GRANT_TYPE, checked: false, disabled: false },
  ];

  constructor(
    @Inject(DomainStoreService) private domainStore: DomainStoreService,
    private trustDomainService: TrustDomainService,
  ) {}

  ngOnInit() {
    this.oauthSettings = this.oauthSettings || {};
    // Ensure jwks formatting similar to original component
    if (this.oauthSettings.jwks && typeof this.oauthSettings.jwks !== 'string') {
      this.oauthSettings.jwks = JSON.stringify(this.oauthSettings.jwks, null, 2);
    }
    this.oauthSettings.tokenExchangeOAuthSettings ??= {
      inherited: true,
      scopeHandling: this.DEFAULT_TOKEN_EXCHANGE_SCOPE_HANDLING,
    };

    this.initTokenEndpointAuthMethods();
    this.initGrantTypes();
    this.initCustomGrantTypes();
    this.initSpiffeSettings();
  }

  private initSpiffeSettings() {
    this.spiffeSettings = this.spiffeSettings || {};
    if (this.isSpiffeEnabledAtDomain() && this.domainId) {
      this.trustDomainService.list(this.domainId).subscribe({
        next: (results) => (this.trustDomains = results || []),
        error: () => (this.trustDomains = []),
      });
    }
  }

  isSpiffeEnabledAtDomain(): boolean {
    return this.domainStore.current?.oidc?.workloadIdentitySettings?.enabled === true;
  }

  spiffeChanged() {
    this.spiffeSettingsChange.emit({ ...this.spiffeSettings });
    this.formChanged.emit(true);
  }

  isAgentPrefixCapable(): boolean {
    return (
      (this.applicationType || '').toUpperCase() === 'AGENT' &&
      (this.applicationKind === 'HOSTED_DELEGATED' || this.applicationKind === 'AUTONOMOUS')
    );
  }

  // Helper getters
  get selectedGrantTypes() {
    return this.grantTypes.filter((grantType) => grantType.checked).map((grantType) => grantType.value);
  }

  get selectedCustomGrantTypes() {
    return this.customGrantTypes.filter((grantType) => grantType.checked).map((grantType) => grantType.value);
  }

  get filteredGrantTypes() {
    if (this.context === this.MCP_SERVER_CONTEXT) {
      return this.grantTypes.filter(
        (grantType) => grantType.value === this.CLIENT_CREDENTIALS_GRANT_TYPE || grantType.value === this.TOKEN_EXCHANGE_GRANT_TYPE,
      );
    }
    return this.grantTypes;
  }

  get filteredTokenEndpointAuthMethods() {
    if (this.context === this.MCP_SERVER_CONTEXT) {
      const allowedMethods = ['client_secret_basic', 'client_secret_post', 'client_secret_jwt'];
      return this.tokenEndpointAuthMethods.filter((method) => allowedMethods.includes(method.value));
    }
    if (!this.isSpiffeEnabledAtDomain()) {
      return this.tokenEndpointAuthMethods.filter((method) => method.value !== 'spiffe_jwt');
    }
    return this.tokenEndpointAuthMethods;
  }

  customGrantTypeIsDisabled(extensionGrant): boolean {
    return !extensionGrant.checked && this.selectedCustomGrantTypes.length > 0;
  }

  isGrantTypeVisuallyChecked(grantType: any): boolean {
    if (grantType.disabled) {
      if (grantType.value === this.CIBA_GRANT_TYPE || grantType.value === this.TOKEN_EXCHANGE_GRANT_TYPE) {
        return false;
      }
    }
    return grantType.checked;
  }

  selectGrantType(event) {
    this.grantTypes.filter((grantType) => grantType.value === event.source.value).map((grantType) => (grantType.checked = event.checked));
    this.modelChanged();
  }

  selectCustomGrantType(event) {
    this.customGrantTypes
      .filter((grantType) => grantType.value === event.source.value)
      .map((grantType) => (grantType.checked = event.checked));
    this.modelChanged();
  }

  forcePKCE(event) {
    this.oauthSettings.forcePKCE = event.checked;
    this.modelChanged();
  }

  isPKCEForced() {
    return this.oauthSettings.forcePKCE;
  }

  forceS256CodeChallengeMethod(event) {
    this.oauthSettings.forceS256CodeChallengeMethod = event.checked;
    this.modelChanged();
  }

  isS256CodeChallengeMethodForced() {
    return this.oauthSettings.forceS256CodeChallengeMethod;
  }

  dpopBoundAccessTokens(event) {
    this.oauthSettings.dpopBoundAccessTokens = event.checked;
    this.modelChanged();
  }

  isDpopBoundAccessTokens() {
    return this.oauthSettings.dpopBoundAccessTokens;
  }

  isRefreshTokenFlowSelected() {
    return this.selectedGrantTypes.includes('refresh_token');
  }

  isTokenExchangeFlowSelected(): boolean {
    return this.selectedGrantTypes.includes(this.TOKEN_EXCHANGE_GRANT_TYPE);
  }

  isTokenExchangeEnabledAtDomain(): boolean {
    return this.domainStore.current?.tokenExchangeSettings?.enabled === true;
  }

  isTokenExchangeInherited(): boolean {
    return this.oauthSettings.tokenExchangeOAuthSettings?.inherited !== false;
  }

  enableTokenExchangeInherit(event: any) {
    this.oauthSettings.tokenExchangeOAuthSettings = {
      ...this.oauthSettings.tokenExchangeOAuthSettings,
      inherited: event.checked,
    };
    this.modelChanged();
  }

  tokenExchangeScopeHandlingChanged(value: TokenExchangeScopeHandling) {
    this.oauthSettings.tokenExchangeOAuthSettings = {
      ...this.oauthSettings.tokenExchangeOAuthSettings,
      scopeHandling: value,
    };
    this.modelChanged();
  }

  disableRefreshTokenRotation(event) {
    this.oauthSettings.disableRefreshTokenRotation = event.checked;
    this.modelChanged();
  }

  isRefreshTokenRotationDisabled() {
    return this.oauthSettings.disableRefreshTokenRotation;
  }

  tokenEndpointAuthMethodChanged(value) {
    const clientCredentials = this.grantTypes.find((grantType) => grantType.value === this.CLIENT_CREDENTIALS_GRANT_TYPE);
    if ('none' === value) {
      clientCredentials.checked = false;
      clientCredentials.disabled = true;
    } else {
      clientCredentials.disabled = false;
    }
    this.modelChanged();
  }

  modelChanged() {
    this.formChanged.emit(true);
    // Prepare updated settings object to emit
    // For MCP Server context, include client_credentials, token exchange, and custom grant types
    let selectedGrantTypes = this.selectedGrantTypes;
    if (this.context === this.MCP_SERVER_CONTEXT) {
      selectedGrantTypes = selectedGrantTypes.filter(
        (gt) => gt === this.CLIENT_CREDENTIALS_GRANT_TYPE || gt === this.TOKEN_EXCHANGE_GRANT_TYPE,
      );
    }
    const updatedSettings = {
      ...this.oauthSettings,
      grantTypes: selectedGrantTypes.concat(this.selectedCustomGrantTypes),
    };
    this.settingsChange.emit(updatedSettings);
  }

  private initTokenEndpointAuthMethods() {
    if (this.applicationType === 'service') {
      this.tokenEndpointAuthMethods = this.tokenEndpointAuthMethods.map((item) => {
        if (item.value === 'none') {
          item.disabled = true;
        }
        return item;
      });
    }

    const hashIds = this.secretSettings.filter((settings) => settings.algorithm.toUpperCase() !== 'NONE').map((settings) => settings.id);
    if (hashIds?.length !== 0 && this.oauthSettings.tokenEndpointAuthMethod !== 'client_secret_jwt') {
      this.tokenEndpointAuthMethods = this.tokenEndpointAuthMethods.filter((item) => item.value !== 'client_secret_jwt');
    }
  }

  private initGrantTypes() {
    const grantTypesList = this.oauthSettings.grantTypes || [];
    // For MCP Server context, filter grant types list to include client_credentials and token exchange
    let filteredGrantTypesList = grantTypesList;
    if (this.context === this.MCP_SERVER_CONTEXT) {
      filteredGrantTypesList = grantTypesList.filter(
        (gt) =>
          gt.toLowerCase() === this.CLIENT_CREDENTIALS_GRANT_TYPE || gt.toLowerCase() === this.TOKEN_EXCHANGE_GRANT_TYPE.toLowerCase(),
      );
    }
    this.grantTypes.forEach((grantType) => {
      grantType.checked = some(
        filteredGrantTypesList,
        (authorizedGrantType) => authorizedGrantType.toLowerCase() === grantType.value.toLowerCase(),
      );
      if (
        this.oauthSettings.tokenEndpointAuthMethod &&
        'none' === this.oauthSettings.tokenEndpointAuthMethod &&
        this.CLIENT_CREDENTIALS_GRANT_TYPE === grantType.value
      ) {
        grantType.disabled = true;
      }
      if (this.CIBA_GRANT_TYPE === grantType.value && this.domainStore.current) {
        const domain = deepClone(this.domainStore.current);
        grantType.disabled = domain.oidc.cibaSettings && !domain.oidc.cibaSettings.enabled;
      }
      if (this.TOKEN_EXCHANGE_GRANT_TYPE === grantType.value && this.domainStore.current) {
        const domain = deepClone(this.domainStore.current);
        grantType.disabled = !domain.tokenExchangeSettings || !domain.tokenExchangeSettings.enabled;
      }
    });
  }

  private initCustomGrantTypes() {
    const grantTypesList = this.oauthSettings.grantTypes || [];
    const oldestExtensionGrant = minBy(this.customGrantTypes, 'createdAt');
    this.customGrantTypes.forEach((customGrantType) => {
      customGrantType.value = customGrantType.grantType + '~' + customGrantType.id;
      customGrantType.checked = some(grantTypesList, (authorizedGrantType) => {
        return (
          authorizedGrantType.toLowerCase() === customGrantType.value.toLowerCase() ||
          (oldestExtensionGrant &&
            authorizedGrantType.toLowerCase() === customGrantType.grantType &&
            customGrantType.createdAt === oldestExtensionGrant.createdAt)
        );
      });
    });
  }
}
