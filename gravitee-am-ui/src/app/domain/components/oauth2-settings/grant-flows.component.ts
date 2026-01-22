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
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { some, minBy } from 'lodash';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { DomainStoreService } from '../../../stores/domain.store';

@Component({
  selector: 'app-grant-flows-settings',
  templateUrl: './grant-flows.component.html',
  styleUrls: ['./grant-flows.component.scss'],
  standalone: false,
})
export class GrantFlowsComponent implements OnInit {
  @Input() oauthSettings: any; // OAuth settings input
  @Input() domainId: string; // Domain ID from parent
  @Input() context: 'Application' | 'McpServer' = 'Application'; // context to toggle features

  // Optional: Full application object if needed for specific logic (like tokenEndpointAuthMethods filtering)
  // Or better, pass specifically what is needed.
  @Input() secretSettings: any[] = [];
  @Input() applicationType: string = 'service'; // Default to service if not provided? Or make optional

  @Input() customGrantTypes: any[] = [];
  @Input() readonly = false;

  @Output() settingsChange = new EventEmitter<any>();
  @Output() formChanged = new EventEmitter<boolean>();

  private CIBA_GRANT_TYPE = 'urn:openid:params:grant-type:ciba';

  tokenEndpointAuthMethods: any[] = [
    { name: 'client_secret_basic', value: 'client_secret_basic' },
    { name: 'client_secret_post', value: 'client_secret_post' },
    { name: 'client_secret_jwt', value: 'client_secret_jwt' },
    { name: 'private_key_jwt', value: 'private_key_jwt' },
    { name: 'Mutual TLS - PKI Mutual (tls_client_auth)', value: 'tls_client_auth' },
    { name: 'Mutual TLS - Self-Signed Certificate Mutual (self_signed_tls_client_auth)', value: 'self_signed_tls_client_auth' },
    { name: 'none', value: 'none' },
  ];

  grantTypes: any[] = [
    { name: 'AUTHORIZATION CODE', value: 'authorization_code', checked: false, disabled: false },
    { name: 'IMPLICIT', value: 'implicit', checked: false, disabled: false },
    { name: 'REFRESH TOKEN', value: 'refresh_token', checked: false, disabled: false },
    { name: 'PASSWORD', value: 'password', checked: false },
    { name: 'CLIENT CREDENTIALS', value: 'client_credentials', checked: false, disabled: false },
    { name: 'UMA TICKET', value: 'urn:ietf:params:oauth:grant-type:uma-ticket', checked: false, disabled: false },
    { name: 'CIBA', value: this.CIBA_GRANT_TYPE, checked: false, disabled: false },
  ];

  constructor(private domainStore: DomainStoreService) {}

  ngOnInit() {
    this.oauthSettings = this.oauthSettings || {};
    // Ensure jwks formatting similar to original component
    if (this.oauthSettings.jwks && typeof this.oauthSettings.jwks !== 'string') {
      this.oauthSettings.jwks = JSON.stringify(this.oauthSettings.jwks, null, 2);
    }

    this.initTokenEndpointAuthMethods();
    this.initGrantTypes();
    this.initCustomGrantTypes();
  }

  // Helper getters
  get selectedGrantTypes() {
    return this.grantTypes.filter((grantType) => grantType.checked).map((grantType) => grantType.value);
  }

  get selectedCustomGrantTypes() {
    return this.customGrantTypes.filter((grantType) => grantType.checked).map((grantType) => grantType.value);
  }

  get filteredGrantTypes() {
    // For MCP Server context, only show client_credentials and custom grant types (for token exchange)
    if (this.context === 'McpServer') {
      return this.grantTypes.filter((grantType) => grantType.value === 'client_credentials');
    }
    return this.grantTypes;
  }

  customGrantTypeIsDisabled(extensionGrant): boolean {
    return !extensionGrant.checked && this.selectedCustomGrantTypes.length > 0;
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

  isRefreshTokenFlowSelected() {
    return this.selectedGrantTypes.includes('refresh_token');
  }

  disableRefreshTokenRotation(event) {
    this.oauthSettings.disableRefreshTokenRotation = event.checked;
    this.modelChanged();
  }

  isRefreshTokenRotationDisabled() {
    return this.oauthSettings.disableRefreshTokenRotation;
  }

  tokenEndpointAuthMethodChanged(value) {
    const clientCredentials = this.grantTypes.find((grantType) => grantType.value === 'client_credentials');
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
    // For MCP Server context, only include client_credentials and custom grant types
    let selectedGrantTypes = this.selectedGrantTypes;
    if (this.context === 'McpServer') {
      selectedGrantTypes = selectedGrantTypes.filter((gt) => gt === 'client_credentials');
    }
    const updatedSettings = {
      ...this.oauthSettings,
      grantTypes: selectedGrantTypes.concat(this.selectedCustomGrantTypes),
      // jwks parsing handled by parent on save usually, but we keep the string version in local state
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
    // For MCP Server context, filter grant types list to only include client_credentials and custom grant types
    let filteredGrantTypesList = grantTypesList;
    if (this.context === 'McpServer') {
      filteredGrantTypesList = grantTypesList.filter(
        (gt) => gt.toLowerCase() === 'client_credentials' || this.isCustomGrantType(gt),
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
        'client_credentials' === grantType.value
      ) {
        grantType.disabled = true;
      }
      if (this.CIBA_GRANT_TYPE === grantType.value && this.domainStore.current) {
        const domain = deepClone(this.domainStore.current);
        grantType.disabled = domain.oidc.cibaSettings && !domain.oidc.cibaSettings.enabled;
      }
    });
  }

  private isCustomGrantType(grantType: string): boolean {
    return this.customGrantTypes.some(
      (customGrantType) =>
        grantType.toLowerCase() === (customGrantType.grantType + '~' + customGrantType.id).toLowerCase() ||
        grantType.toLowerCase() === customGrantType.grantType.toLowerCase(),
    );
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
