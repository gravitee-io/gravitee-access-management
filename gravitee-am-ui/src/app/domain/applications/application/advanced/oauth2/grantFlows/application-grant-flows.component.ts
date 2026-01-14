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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { map, minBy, some } from 'lodash';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { ApplicationService } from '../../../../../../services/application.service';
import { SnackbarService } from '../../../../../../services/snackbar.service';
import { AuthService } from '../../../../../../services/auth.service';
import { DomainStoreService } from '../../../../../../stores/domain.store';

@Component({
  selector: 'application-grant-types',
  templateUrl: './application-grant-flows.component.html',
  styleUrls: ['./application-grant-flows.component.scss'],
  standalone: false,
})
export class ApplicationGrantFlowsComponent implements OnInit {
  private CIBA_GRANT_TYPE = 'urn:openid:params:grant-type:ciba';
  private TOKEN_EXCHANGE_GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:token-exchange';
  private domainId: string;
  formChanged: boolean;
  application: any;
  applicationOauthSettings: any = {};
  readonly = false;
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
    { name: 'TOKEN EXCHANGE', value: this.TOKEN_EXCHANGE_GRANT_TYPE, checked: false, disabled: false },
    { name: 'CIBA', value: this.CIBA_GRANT_TYPE, checked: false, disabled: false },
  ];
  customGrantTypes: any[];
  config: any = {};

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private applicationService: ApplicationService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = structuredClone(this.route.snapshot.data['application']);
    this.customGrantTypes = this.route.snapshot.data['domainGrantTypes'] || [];
    this.applicationOauthSettings = this.application.settings == null ? {} : this.application.settings.oauth || {};
    this.applicationOauthSettings.jwks = this.applicationOauthSettings.jwks
      ? JSON.stringify(this.applicationOauthSettings.jwks, null, 2)
      : null;
    this.readonly = !this.authService.hasPermissions(['application_openid_update']);
    this.initTokenEndpointAuthMethods();
    this.initGrantTypes();
    this.initCustomGrantTypes();
  }

  patch() {
    // check configuration
    if (this.applicationOauthSettings.tokenEndpointAuthMethod === 'private_key_jwt') {
      if (!this.applicationOauthSettings.jwksUri && !this.applicationOauthSettings.jwks) {
        this.snackbarService.open("The jwks_uri or jwks are required when using 'private_key_jwt' client authentication method");
        return;
      }
      if (this.applicationOauthSettings.jwksUri && this.applicationOauthSettings.jwks) {
        this.snackbarService.open('The jwks_uri and jwks parameters MUST NOT be used together.');
        return;
      }
      if (this.applicationOauthSettings.jwks) {
        try {
          JSON.parse(this.applicationOauthSettings.jwks);
        } catch {
          this.snackbarService.open('The jwks parameter is malformed.');
          return;
        }
      }
    }

    const oauthSettings: any = {};
    oauthSettings.grantTypes = this.selectedGrantTypes.concat(this.selectedCustomGrantTypes);
    oauthSettings.forcePKCE = this.applicationOauthSettings.forcePKCE;
    oauthSettings.forceS256CodeChallengeMethod = this.applicationOauthSettings.forceS256CodeChallengeMethod;
    oauthSettings.tokenEndpointAuthMethod = this.applicationOauthSettings.tokenEndpointAuthMethod;
    oauthSettings.tlsClientAuthSubjectDn = this.applicationOauthSettings.tlsClientAuthSubjectDn;
    oauthSettings.tlsClientAuthSanDns = this.applicationOauthSettings.tlsClientAuthSanDns;
    oauthSettings.tlsClientAuthSanUri = this.applicationOauthSettings.tlsClientAuthSanUri;
    oauthSettings.tlsClientAuthSanIp = this.applicationOauthSettings.tlsClientAuthSanIp;
    oauthSettings.tlsClientAuthSanEmail = this.applicationOauthSettings.tlsClientAuthSanEmail;
    oauthSettings.jwksUri = this.applicationOauthSettings.jwksUri;
    oauthSettings.jwks = this.applicationOauthSettings.jwks ? JSON.parse(this.applicationOauthSettings.jwks) : null;
    oauthSettings.disableRefreshTokenRotation = this.applicationOauthSettings.disableRefreshTokenRotation;
    this.applicationService.patch(this.domainId, this.application.id, { settings: { oauth: oauthSettings } }).subscribe(() => {
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
      this.formChanged = false;
    });
  }

  customGrantTypeIsDisabled(extensionGrant): boolean {
    return !extensionGrant.checked && this.selectedCustomGrantTypes.length > 0;
  }

  selectGrantType(event) {
    this.grantTypes.filter((grantType) => grantType.value === event.source.value).map((grantType) => (grantType.checked = event.checked));
    this.formChanged = true;
  }

  selectCustomGrantType(event) {
    this.customGrantTypes
      .filter((grantType) => grantType.value === event.source.value)
      .map((grantType) => (grantType.checked = event.checked));
    this.formChanged = true;
  }

  forcePKCE(event) {
    this.applicationOauthSettings.forcePKCE = event.checked;
    this.formChanged = true;
  }

  isPKCEForced() {
    return this.applicationOauthSettings.forcePKCE;
  }

  forceS256CodeChallengeMethod(event) {
    this.applicationOauthSettings.forceS256CodeChallengeMethod = event.checked;
    this.formChanged = true;
  }

  isS256CodeChallengeMethodForced() {
    return this.applicationOauthSettings.forceS256CodeChallengeMethod;
  }

  isRefreshTokenFlowSelected() {
    return this.selectedGrantTypes.includes('refresh_token');
  }

  disableRefreshTokenRotation(event) {
    this.applicationOauthSettings.disableRefreshTokenRotation = event.checked;
    this.formChanged = true;
  }

  isRefreshTokenRotationDisabled() {
    return this.applicationOauthSettings.disableRefreshTokenRotation;
  }

  get selectedGrantTypes() {
    return this.grantTypes.filter((grantType) => grantType.checked).map((grantType) => grantType.value);
  }

  get selectedCustomGrantTypes() {
    return this.customGrantTypes.filter((grantType) => grantType.checked).map((grantType) => grantType.value);
  }

  tokenEndpointAuthMethodChanged(value) {
    const clientCredentials = this.grantTypes.find((grantType) => grantType.value === 'client_credentials');
    if ('none' === value) {
      // remove client_credentials flow if 'none' token endpoint auth method is selected
      clientCredentials.checked = false;
      clientCredentials.disabled = true;
    } else {
      clientCredentials.disabled = false;
    }
    this.modelChanged();
  }

  modelChanged() {
    this.formChanged = true;
  }

  private initTokenEndpointAuthMethods() {
    // disabled none (public client) for service application
    if (this.application.type === 'service') {
      this.tokenEndpointAuthMethods = map(this.tokenEndpointAuthMethods, (item) => {
        if (item.value === 'none') {
          item.disabled = true;
        }
        return item;
      });
    }
    // hide client_secret_jwt if the app has at least one secret hashed.
    // For now, as we only manage one secret we don't have to go through secrets
    // but when multiple secret will be available, we will have to check if a secret
    // is not using the NONE alg.
    const secretSettings = this.application.secretSettings || [];
    const hashIds = secretSettings.filter((settings) => settings.algorithm.toUpperCase() !== 'NONE').map((settings) => settings.id);
    if (hashIds?.length !== 0 && this.application.settings.oauth.tokenEndpointAuthMethod !== 'client_secret_jwt') {
      this.tokenEndpointAuthMethods = this.tokenEndpointAuthMethods.filter((item) => item.value !== 'client_secret_jwt');
    }
  }

  private initGrantTypes() {
    this.grantTypes.forEach((grantType) => {
      grantType.checked = some(
        this.applicationOauthSettings.grantTypes,
        (authorizedGrantType) => authorizedGrantType.toLowerCase() === grantType.value.toLowerCase(),
      );
      if (
        this.applicationOauthSettings.tokenEndpointAuthMethod &&
        'none' === this.applicationOauthSettings.tokenEndpointAuthMethod &&
        'client_credentials' === grantType.value
      ) {
        grantType.disabled = true;
      }
      if (this.CIBA_GRANT_TYPE === grantType.value && this.route.snapshot.data['domain']) {
        const domain = deepClone(this.domainStore.current);
        grantType.disabled = domain.oidc.cibaSettings && !domain.oidc.cibaSettings.enabled;
      }
      if (this.TOKEN_EXCHANGE_GRANT_TYPE === grantType.value && this.route.snapshot.data['domain']) {
        const domain = deepClone(this.domainStore.current);
        grantType.disabled = domain.oidc.tokenExchangeSettings && !domain.oidc.tokenExchangeSettings.enabled;
      }
    });
  }

  private initCustomGrantTypes() {
    const oldestExtensionGrant = minBy(this.customGrantTypes, 'createdAt');
    this.customGrantTypes.forEach((customGrantType) => {
      customGrantType.value = customGrantType.grantType + '~' + customGrantType.id;
      customGrantType.checked = some(this.applicationOauthSettings.grantTypes, (authorizedGrantType) => {
        return (
          authorizedGrantType.toLowerCase() === customGrantType.value.toLowerCase() ||
          (authorizedGrantType.toLowerCase() === customGrantType.grantType && customGrantType.createdAt === oldestExtensionGrant.createdAt)
        );
      });
    });
  }
}
