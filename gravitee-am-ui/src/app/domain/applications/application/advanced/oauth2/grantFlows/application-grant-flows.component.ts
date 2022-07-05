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
import {Component, OnInit} from '@angular/core'
import {ActivatedRoute, Router} from "@angular/router";
import {ApplicationService} from "../../../../../../services/application.service";
import {SnackbarService} from "../../../../../../services/snackbar.service";
import {AuthService} from "../../../../../../services/auth.service";
import * as _ from "lodash";

@Component({
  selector: 'application-grant-types',
  templateUrl: './application-grant-flows.component.html',
  styleUrls: ['./application-grant-flows.component.scss']
})

export class ApplicationGrantFlowsComponent implements OnInit {
  private CIBA_GRANT_TYPE = 'urn:openid:params:grant-type:ciba';
  private domainId: string;
  formChanged: boolean;
  application: any;
  applicationOauthSettings: any = {};
  readonly = false;
  tokenEndpointAuthMethods: any[] = [
    { name : 'client_secret_basic', value: 'client_secret_basic'},
    { name : 'client_secret_post', value: 'client_secret_post'},
    { name : 'client_secret_jwt', value: 'client_secret_jwt'},
    { name : 'private_key_jwt', value: 'private_key_jwt'},
    { name : 'Mutual TLS - PKI Mutual (tls_client_auth)', value: 'tls_client_auth'},
    { name : 'Mutual TLS - Self-Signed Certificate Mutual (self_signed_tls_client_auth)', value: 'self_signed_tls_client_auth'},
    { name : 'none', value: 'none'}
  ];
  grantTypes: any[] = [
    { name: 'AUTHORIZATION CODE', value: 'authorization_code', checked: false, disabled: false },
    { name: 'IMPLICIT', value: 'implicit', checked: false, disabled: false  },
    { name: 'REFRESH TOKEN', value: 'refresh_token', checked: false, disabled: false  },
    { name: 'PASSWORD', value: 'password', checked: false },
    { name: 'CLIENT CREDENTIALS', value: 'client_credentials', checked: false, disabled: false  },
    { name: 'UMA TICKET', value: 'urn:ietf:params:oauth:grant-type:uma-ticket', checked: false, disabled: false  },
    { name: 'CIBA', value: this.CIBA_GRANT_TYPE, checked: false, disabled: false  }
  ];
  customGrantTypes: any[];

  constructor(private route: ActivatedRoute,
              private router: Router,
              private applicationService: ApplicationService,
              private snackbarService: SnackbarService,
              private authService: AuthService) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.customGrantTypes = this.route.snapshot.data['domainGrantTypes'] || [];
    this.applicationOauthSettings = (this.application.settings == null) ? {} : this.application.settings.oauth || {};
    this.readonly = !this.authService.hasPermissions(['application_openid_update']);
    this.initTokenEndpointAuthMethods();
    this.initGrantTypes();
    this.initCustomGrantTypes();
  }

  patch() {
    let oauthSettings: any = {};
    oauthSettings.grantTypes = this.selectedGrantTypes.concat(this.selectedCustomGrantTypes);
    oauthSettings.forcePKCE = this.applicationOauthSettings.forcePKCE;
    oauthSettings.forceS256CodeChallengeMethod = this.applicationOauthSettings.forceS256CodeChallengeMethod;
    oauthSettings.tokenEndpointAuthMethod = this.applicationOauthSettings.tokenEndpointAuthMethod;
    oauthSettings.tlsClientAuthSubjectDn = this.applicationOauthSettings.tlsClientAuthSubjectDn;
    oauthSettings.tlsClientAuthSanDns = this.applicationOauthSettings.tlsClientAuthSanDns;
    oauthSettings.tlsClientAuthSanUri = this.applicationOauthSettings.tlsClientAuthSanUri;
    oauthSettings.tlsClientAuthSanIp = this.applicationOauthSettings.tlsClientAuthSanIp;
    oauthSettings.tlsClientAuthSanEmail = this.applicationOauthSettings.tlsClientAuthSanEmail;
    this.applicationService.patch(this.domainId, this.application.id, {'settings' : { 'oauth' : oauthSettings}}).subscribe(data => {
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { 'reload': true }});
      this.formChanged = false;
    });
  }

  customGrantTypeIsDisabled(extensionGrant): boolean {
    return !extensionGrant.checked && this.selectedCustomGrantTypes.length > 0;
  }

  selectGrantType(event) {
    this.grantTypes
      .filter(grantType => grantType.value === event.source.value)
      .map(grantType => grantType.checked = event.checked);
    this.formChanged = true;
  }

  selectCustomGrantType(event) {
    this.customGrantTypes
      .filter(grantType => grantType.value === event.source.value)
      .map(grantType => grantType.checked = event.checked);
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

  get selectedGrantTypes() {
    return this.grantTypes
      .filter(grantType => grantType.checked)
      .map(grantType => grantType.value)
  }

  get selectedCustomGrantTypes() {
    return this.customGrantTypes
      .filter(grantType => grantType.checked)
      .map(grantType => grantType.value)
  }

  tokenEndpointAuthMethodChanged(value) {
    const clientCredentials = this.grantTypes.find(grantType => grantType.value === 'client_credentials');
    if ('none' === value) {
      // remove client_credentials flow if 'none' token endpoint auth method is selected
      clientCredentials.checked = false;
      clientCredentials.disabled = true;
    } else {
      clientCredentials.disabled = false;
    }
    this.modelChanged(value);
  }

  modelChanged(model) {
    this.formChanged = true;
  }

  private initTokenEndpointAuthMethods() {
    // disabled none (public client) for service application
    if (this.application.type === 'service') {
      const updatedAuthMethods =  _.map(this.tokenEndpointAuthMethods, item => {
        if (item.value === 'none') {
          item.disabled = true;
        }
        return item;
      });
      this.tokenEndpointAuthMethods = updatedAuthMethods;
    }
  }

  private initGrantTypes() {
    this.grantTypes.forEach(grantType => {
      grantType.checked = _.some(this.applicationOauthSettings.grantTypes, authorizedGrantType => authorizedGrantType.toLowerCase() === grantType.value.toLowerCase());
      if (this.applicationOauthSettings.tokenEndpointAuthMethod &&
        'none' === this.applicationOauthSettings.tokenEndpointAuthMethod &&
        'client_credentials' === grantType.value) {
        grantType.disabled = true;
      }
      if (this.CIBA_GRANT_TYPE === grantType.value && this.route.snapshot.data['domain']) {
        let domain = this.route.snapshot.data['domain'];
        grantType.disabled = domain.oidc.cibaSettings && !domain.oidc.cibaSettings.enabled;
      }
    })
  }

  private initCustomGrantTypes() {
    const oldestExtensionGrant = _.minBy(this.customGrantTypes, 'createdAt');
    this.customGrantTypes.forEach(customGrantType => {
      customGrantType.value = customGrantType.grantType + '~' + customGrantType.id;
      customGrantType.checked = _.some(this.applicationOauthSettings.grantTypes, authorizedGrantType => {
        return authorizedGrantType.toLowerCase() === customGrantType.value.toLowerCase() ||
          (authorizedGrantType.toLowerCase() === customGrantType.grantType && customGrantType.createdAt === oldestExtensionGrant.createdAt);
      });
    });
  }
}
