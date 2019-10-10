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
import { Component, EventEmitter, OnInit, Output, ViewChild } from '@angular/core';
import { ActivatedRoute } from "@angular/router";
import { SnackbarService } from "../../../../services/snackbar.service";
import { ClientService } from "../../../../services/client.service";
import { Scope } from "../settings/settings.component";
import * as _ from 'lodash';
import * as moment from "moment";
import { NgForm} from "@angular/forms";
import { MatDialog, MatDialogRef}  from "@angular/material";

@Component({
  selector: 'app-client-oauth2',
  templateUrl: './oauth2.component.html',
  styleUrls: ['./oauth2.component.scss']
})
export class ClientOAuth2Component implements OnInit {
  @ViewChild('claimsTable') table: any;
  private domainId: string;
  client: any;
  formChanged: boolean = false;
  responseTypes: any[] = [
    { value:'code', checked:false },
    { value:'id_token', checked:false },
    { value:'token', checked:false }
  ];
  grantTypes: any[] = [
    { name:'AUTHORIZATION CODE', value:'authorization_code', checked:false },
    { name:'IMPLICIT', value:'implicit', checked:false },
    { name:'REFRESH TOKEN', value:'refresh_token', checked:false },
    { name:'PASSWORD', value:'password', checked:false },
    { name:'CLIENT CREDENTIALS', value:'client_credentials', checked:false }
  ];
  customGrantTypes: any[];
  selectedScopes: Scope[];
  selectedScopeApprovals: any;
  scopes: any[] = [];
  editing = {};

  constructor(private route: ActivatedRoute,
              private clientService: ClientService,
              private snackbarService: SnackbarService,
              public dialog: MatDialog) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.client = this.route.snapshot.parent.data['client'];
    this.customGrantTypes = this.route.snapshot.data['domainGrantTypes'];
    this.scopes = this.route.snapshot.data['scopes'];
    this.selectedScopeApprovals = this.client.scopeApprovals || {};
    this.client.redirectUris = this.client.redirectUris || [];
    this.client.scopes =  this.client.scopes || [];
    this.client.tokenCustomClaims = this.client.tokenCustomClaims || [];
    this.initGrantTypes();
    this.initResponseTypes();
    this.initCustomGrantTypes();
    this.initScopes();
    this.initCustomClaims();
  }

  update() {
    this.client.authorizedGrantTypes = this.selectedGrantTypes.concat(this.selectedCustomGrantTypes);
    this.client.responseTypes = this.selectedResponseTypes;
    this.client.scopes = _.map(this.selectedScopes, scope => scope.key);
    this.client.scopeApprovals = this.selectedScopeApprovals;
    this.cleanCustomClaims();
    this.clientService.update(this.domainId, this.client.id, this.client).subscribe(data => {
      this.client = data;
      this.snackbarService.open('Client updated');
      this.formChanged = false;
      this.initGrantTypes();
      this.initResponseTypes();
      this.initCustomGrantTypes();
      this.initCustomClaims();
    });
  }

  initGrantTypes() {
    this.grantTypes.forEach(grantType => {
      grantType.checked = _.some(this.client.authorizedGrantTypes, authorizedGrantType => authorizedGrantType.toLowerCase() === grantType.value.toLowerCase());
    })
  }

  initResponseTypes() {
    this.responseTypes.forEach(responseType => {
      responseType.checked = _.some(this.client.responseTypes, clientResponseType => clientResponseType.toLowerCase() === responseType.value.toLowerCase());
    })
  }

  initCustomGrantTypes() {
    let oldestExtensionGrant = _.minBy(this.customGrantTypes, 'createdAt');
    this.customGrantTypes.forEach(customGrantType => {
      customGrantType.value = customGrantType.grantType + '~' + customGrantType.id;
      customGrantType.checked = _.some(this.client.authorizedGrantTypes, authorizedGrantType => {
        return authorizedGrantType.toLowerCase() === customGrantType.value.toLowerCase()
          || (authorizedGrantType.toLowerCase() === customGrantType.grantType && customGrantType.createdAt === oldestExtensionGrant.createdAt);
      });
    });
  }

  initScopes() {
    let that = this;
    // Merge with existing scope
    this.selectedScopes = _.map(this.client.scopes, function(scope) {
      let definedScope = _.find(that.scopes, { 'key': scope });
      if (definedScope !== undefined) {
        return definedScope;
      }

      return undefined;
    });

    this.scopes = _.difference(this.scopes, this.selectedScopes);
  }

  initCustomClaims() {
    if (this.client.tokenCustomClaims.length > 0) {
      this.client.tokenCustomClaims.forEach(claim => {
        claim.id = Math.random().toString(36).substring(7);
      })
    }
  }

  cleanCustomClaims() {
    if (this.client.tokenCustomClaims.length > 0) {
      this.client.tokenCustomClaims.forEach(claim => {
        delete claim.id;
      })
    }
  }

  selectGrantType(event) {
    this.grantTypes
      .filter(grantType => grantType.value === event.source.value)
      .map(grantType => grantType.checked = event.checked);
    this.updateResponseTypeAccordingGrantType(event);
    this.formChanged = true;
  }

  selectCustomGrantType(event) {
    this.customGrantTypes
      .filter(grantType => grantType.value === event.source.value)
      .map(grantType => grantType.checked = event.checked);
    this.formChanged = true;
  }

  selectResponseType(event) {
    this.responseTypes
      .filter(responseType => responseType.value === event.source.value)
      .map(responseType => responseType.checked = event.checked);
    this.updateGrantTypeAccordingResponseType(event);
    this.formChanged = true;
  }

  updateResponseTypeAccordingGrantType(event) {
    if('authorization_code' === event.source.value) {
      this.responseTypes
        .filter(responseType => responseType.value === 'code')
        .map(responseType => responseType.checked = event.checked);
    }
    else if('implicit' === event.source.value) {
      if(event.checked) {
        if(this.responseTypes
          .filter(responseType => responseType.value === 'token' || responseType.value === 'id_token')
          .filter(responseType => responseType.checked == true)
          .length == 0)
        {
          //select at leas token
          this.responseTypes
            .filter(responseType => responseType.value === 'token')
            .map(responseType => responseType.checked = true);
        }
      } else {
        //disable token & id_token
        this.responseTypes
          .filter(responseType => responseType.value === 'token' || responseType.value === 'id_token')
          .map(responseType => responseType.checked = false);
      }
    }
  }

  updateGrantTypeAccordingResponseType(event) {
    if('code' === event.source.value) {
      this.grantTypes
        .filter(grantType => grantType.value === 'authorization_code')
        .map(grantType => grantType.checked = event.checked);
    }
    else {
      let enableImplicit = this.responseTypes
        .filter(responseType => responseType.value === 'token' || responseType.value === 'id_token')
        .filter(responseType => responseType.checked == true)
        .length > 0 ;
      this.grantTypes
        .filter(grantType => grantType.value === 'implicit')
        .map(grantType => grantType.checked = enableImplicit);
    }
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
    this.client.enhanceScopesWithUserPermissions = event.checked;
    this.formChanged = true;
  }

  isScopesEnhanceWithUserPermissions() {
    return this.client.enhanceScopesWithUserPermissions;
  }

  get isSelectedScopesEmpty() {
    return !this.selectedScopes || this.selectedScopes.length == 0;
  }

  getScopeApproval(scopeKey) {
    let scopeApproval = this.selectedScopeApprovals[scopeKey];
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

  customGrantTypeIsDisabled(extensionGrant): boolean {
    return !extensionGrant.checked && this.selectedCustomGrantTypes.length > 0;
  }

  get selectedResponseTypes() {
    return this.responseTypes
      .filter(responseType => responseType.checked)
      .map(responseType => responseType.value)
  }

  addRedirectURI(event, input: HTMLInputElement) {
    this.addElement(input, this.client.redirectUris, event);
  }

  removeRedirectURI(redirectURI, event) {
    this.removeElement(redirectURI, this.client.redirectUris, event);
  }

  addElement(input: HTMLInputElement, list: any[], event: any) {
    event.preventDefault();
    if (input.value && input.value.trim() != '' && list.indexOf(input.value.trim()) == -1) {
      list.push(input.value.trim());
      input.value = '';
      this.formChanged = true;
    }
  }

  removeElement(element: any, list: any[], event: any) {
    event.preventDefault();
    let index = list.indexOf(element);
    if (index !== -1) {
      list.splice(index, 1);
      this.formChanged = true;
    }
  }

  addClaim(claim) {
    if (claim) {
      if (!this.claimExits(claim.tokenType, claim.claimName)) {
        claim.id = Math.random().toString(36).substring(7);
        this.client.tokenCustomClaims.push(claim);
        this.client.tokenCustomClaims = [...this.client.tokenCustomClaims];
        this.formChanged = true;
      } else {
        this.snackbarService.open(`Error : claim ${claim.claimName} already exists`);
      }
    }
  }

  updateClaim(tokenType, event, cell, rowIndex) {
    let claim = event.target.value;
    if (claim) {
      if (cell === 'claimName' && this.claimExits(tokenType, claim)) {
        this.snackbarService.open(`Error : claim ${claim} already exists`);
        return;
      }
      this.editing[rowIndex + '-' + cell] = false;
      let index = _.findIndex(this.client.tokenCustomClaims, {id: rowIndex});
      this.client.tokenCustomClaims[index][cell] = claim;
      this.client.tokenCustomClaims = [...this.client.tokenCustomClaims];
      this.formChanged = true;
    }
  }

  deleteClaim(tokenType, key, event) {
    event.preventDefault();
    _.remove(this.client.tokenCustomClaims, function(el) {
      return (el.tokenType === tokenType && el.claimName === key);
    });
    this.client.tokenCustomClaims = [...this.client.tokenCustomClaims];
    this.formChanged = true;
  }

  claimExits(tokenType, attribute): boolean {
    return _.find(this.client.tokenCustomClaims, function(el) { return  el.tokenType === tokenType && el.claimName === attribute; })
  }

  claimsIsEmpty() {
    return this.client.tokenCustomClaims.length == 0;
  }

  openDialog(event) {
    event.preventDefault();
    this.dialog.open(ClaimsInfoDialog, {});
  }

  toggleExpandGroup(group) {
    this.table.groupHeader.toggleExpandGroup(group);
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
  @ViewChild('claimForm') form: NgForm;

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
