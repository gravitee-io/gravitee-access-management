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
import {Component, EventEmitter, OnInit, Output, ViewChild} from '@angular/core';
import {NgForm} from '@angular/forms';
import {MatDialog} from '@angular/material';
import {MatDialogRef} from '@angular/material/dialog';
import {ActivatedRoute} from '@angular/router';
import {ApplicationService} from '../../../../../services/application.service';
import {SnackbarService} from '../../../../../services/snackbar.service';
import {AuthService} from '../../../../../services/auth.service';
import * as _ from 'lodash';
import * as moment from 'moment';

@Component({
  selector: 'app-application-oauth2',
  templateUrl: './oauth2.component.html',
  styleUrls: ['./oauth2.component.scss']
})
export class ApplicationOAuth2Component implements OnInit {
  @ViewChild('claimsTable', { static: false }) table: any;
  @ViewChild('clientOAuth2Form', { static: true }) form: any;
  private domainId: string;
  application: any;
  formChanged = false;
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
    { name: 'UMA TICKET', value: 'urn:ietf:params:oauth:grant-type:uma-ticket', checked: false, disabled: false  }
  ];
  customGrantTypes: any[];
  selectedScopes: any[];
  selectedScopeApprovals: any;
  scopes: any[] = [];
  editing: any = {};
  applicationOauthSettings: any = {};
  readonly = false;

  constructor(private route: ActivatedRoute,
              private applicationService: ApplicationService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              public dialog: MatDialog) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.parent.params['domainId'];
    this.application = this.route.snapshot.parent.parent.data['application'];
    this.customGrantTypes = this.route.snapshot.data['domainGrantTypes'];
    this.scopes = this.route.snapshot.data['scopes'];
    this.readonly = !this.authService.hasPermissions(['application_openid_update']);
    this.initSettings();
  }

  update() {
    this.applicationOauthSettings.grantTypes = this.selectedGrantTypes.concat(this.selectedCustomGrantTypes);
    this.applicationOauthSettings.scopes = _.map(this.selectedScopes, scope => scope.key);
    this.applicationOauthSettings.scopeApprovals = this.selectedScopeApprovals;
    delete this.applicationOauthSettings.clientId;
    delete this.applicationOauthSettings.clientSecret;
    delete this.applicationOauthSettings.clientType;
    delete this.applicationOauthSettings.applicationType;
    // currently jwks are managed via the DCR, remove it from the UI
    delete this.applicationOauthSettings.jwks;
    this.cleanCustomClaims();
    this.applicationService.patch(this.domainId, this.application.id, {'settings' : { 'oauth' : this.applicationOauthSettings}}).subscribe(data => {
      this.application = data;
      this.route.snapshot.parent.parent.data['application'] = this.application;
      this.scopes = this.route.snapshot.data['scopes'];
      this.applicationOauthSettings = {};
      this.formChanged = false;
      this.initSettings();
      this.snackbarService.open('Application updated');
    });
  }

  initSettings() {
    this.applicationOauthSettings = (this.application.settings == null) ? {} : this.application.settings.oauth || {};
    this.selectedScopeApprovals = this.applicationOauthSettings.scopeApprovals || {};
    this.applicationOauthSettings.scopes =  this.applicationOauthSettings.scopes || [];
    this.applicationOauthSettings.tokenCustomClaims = this.applicationOauthSettings.tokenCustomClaims || [];
    this.initTokenEndpointAuthMethods();
    this.initGrantTypes();
    this.initCustomGrantTypes();
    this.initScopes();
    this.initCustomClaims();
  }

  initTokenEndpointAuthMethods() {
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

  initGrantTypes() {
    this.grantTypes.forEach(grantType => {
      grantType.checked = _.some(this.applicationOauthSettings.grantTypes, authorizedGrantType => authorizedGrantType.toLowerCase() === grantType.value.toLowerCase());
      if (this.applicationOauthSettings.tokenEndpointAuthMethod &&
        'none' === this.applicationOauthSettings.tokenEndpointAuthMethod &&
        'client_credentials' === grantType.value) {
        grantType.disabled = true;
      }
    })
  }

  initCustomGrantTypes() {
    const oldestExtensionGrant = _.minBy(this.customGrantTypes, 'createdAt');
    this.customGrantTypes.forEach(customGrantType => {
      customGrantType.value = customGrantType.grantType + '~' + customGrantType.id;
      customGrantType.checked = _.some(this.applicationOauthSettings.grantTypes, authorizedGrantType => {
        return authorizedGrantType.toLowerCase() === customGrantType.value.toLowerCase() ||
          (authorizedGrantType.toLowerCase() === customGrantType.grantType && customGrantType.createdAt === oldestExtensionGrant.createdAt);
      });
    });
  }

  customGrantTypeIsDisabled(extensionGrant): boolean {
    return !extensionGrant.checked && this.selectedCustomGrantTypes.length > 0;
  }

  initScopes() {
    // Merge with existing scope
    this.selectedScopes = [];
    this.applicationOauthSettings.scopes.forEach(scope => {
      const definedScope = _.find(this.scopes, {key: scope});
      if (definedScope) {
        this.selectedScopes.push(definedScope);
      }
    });

    this.scopes = _.difference(this.scopes, this.selectedScopes);
  }

  initCustomClaims() {
    if (this.applicationOauthSettings.tokenCustomClaims.length > 0) {
      this.applicationOauthSettings.tokenCustomClaims.forEach(claim => {
        claim.id = Math.random().toString(36).substring(7);
      })
    }
  }

  cleanCustomClaims() {
    if (this.applicationOauthSettings.tokenCustomClaims.length > 0) {
      this.applicationOauthSettings.tokenCustomClaims.forEach(claim => {
        delete claim.id;
      })
    }
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

  addScope(scope) {
    this.selectedScopes = this.selectedScopes.concat(_.remove(this.scopes, { 'key': scope.key }));
    this.selectedScopes = [...this.selectedScopes];

    if (scope.expiresIn && scope.unitTime) {
      this.selectedScopeApprovals[scope.key] = moment.duration(scope.expiresIn, scope.unitTime).asSeconds();
    }

    this.formChanged = true;
  }

  removeScope(scopeKey, event) {
    event.preventDefault();
    this.scopes = this.scopes.concat(_.remove(this.selectedScopes, function(selectPermission) {
      return selectPermission.key === scopeKey;
    }));
    this.selectedScopes = [...this.selectedScopes];
    delete this.selectedScopeApprovals[scopeKey];
    this.formChanged = true;
  }

  enhanceScopesWithUserPermissions(event) {
    this.applicationOauthSettings.enhanceScopesWithUserPermissions = event.checked;
    this.formChanged = true;
  }

  isScopesEnhanceWithUserPermissions() {
    return this.applicationOauthSettings.enhanceScopesWithUserPermissions;
  }

  getScopeApproval(scopeKey) {
    const scopeApproval = this.selectedScopeApprovals[scopeKey];
    return this.getScopeExpiry(scopeApproval);
  }

  getScopeExpiry(expiresIn) {
    return (expiresIn) ? moment.duration(expiresIn, 'seconds').humanize() : 'no time set';
  }

  scopeApprovalExists(scopeKey) {
    return this.selectedScopeApprovals.hasOwnProperty(scopeKey);
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

  addClaim(claim) {
    if (claim) {
      if (!this.claimExits(claim)) {
        claim.id = Math.random().toString(36).substring(7);
        this.applicationOauthSettings.tokenCustomClaims.push(claim);
        this.applicationOauthSettings.tokenCustomClaims = [...this.applicationOauthSettings.tokenCustomClaims];
        this.formChanged = true;
      } else {
        this.snackbarService.open('Claim already exists');
      }
    }
  }

  claimExits(claim): boolean {
    return _.find(this.applicationOauthSettings.tokenCustomClaims, function(el) {
      return el.tokenType === claim.tokenType && el.claimName === claim.claimName;
    });
  }

  updateClaim(tokenType, event, cell, rowIndex) {
    const claim = event.target.value;
    if (claim) {
      this.editing[rowIndex + '-' + cell] = false;
      const index = _.findIndex(this.applicationOauthSettings.tokenCustomClaims, {id: rowIndex});
      this.applicationOauthSettings.tokenCustomClaims[index][cell] = claim;
      this.applicationOauthSettings.tokenCustomClaims = [...this.applicationOauthSettings.tokenCustomClaims];
      this.formChanged = true;
    }
  }

  deleteClaim(tokenType, key, event) {
    event.preventDefault();
    _.remove(this.applicationOauthSettings.tokenCustomClaims, function(el) {
      return (el.tokenType === tokenType && el.claimName === key);
    });
    this.applicationOauthSettings.tokenCustomClaims = [...this.applicationOauthSettings.tokenCustomClaims];
    this.formChanged = true;
  }

  claimsIsEmpty() {
    return this.applicationOauthSettings.tokenCustomClaims.length === 0;
  }

  openDialog(event) {
    event.preventDefault();
    this.dialog.open(ClaimsInfoDialog, {});
  }

  toggleExpandGroup(group) {
    this.table.groupHeader.toggleExpandGroup(group);
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
}

@Component({
  selector: 'app-create-claim',
  templateUrl: './claims/add-claim.component.html'
})
export class CreateClaimComponent {
  claim: any = {};
  tokenTypes: any[] = ['ID_TOKEN', 'ACCESS_TOKEN'];
  @Output() addClaimChange = new EventEmitter();
  @ViewChild('claimForm', { static: true }) form: NgForm;

  constructor() {}

  addClaim() {
    this.addClaimChange.emit(this.claim);
    this.claim = {};
    this.form.reset(this.claim);
  }
}

@Component({
  selector: 'claims-info-dialog',
  templateUrl: './dialog/claims-info.component.html',
})
export class ClaimsInfoDialog {
  constructor(public dialogRef: MatDialogRef<ClaimsInfoDialog>) {}
}
