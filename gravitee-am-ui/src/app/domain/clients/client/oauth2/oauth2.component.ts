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
import { ActivatedRoute } from "@angular/router";
import { SnackbarService } from "../../../../services/snackbar.service";
import { ClientService } from "../../../../services/client.service";
import { Scope } from "../settings/settings.component";
import * as _ from 'lodash';
import * as moment from "moment";

@Component({
  selector: 'app-client-oauth2',
  templateUrl: './oauth2.component.html',
  styleUrls: ['./oauth2.component.scss']
})
export class ClientOAuth2Component implements OnInit {
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

  constructor(private route: ActivatedRoute,
              private clientService: ClientService,
              private snackbarService: SnackbarService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.client = this.route.snapshot.parent.data['client'];
    this.customGrantTypes = this.route.snapshot.data['domainGrantTypes'];
    this.scopes = this.route.snapshot.data['scopes'];
    this.selectedScopeApprovals = this.client.scopeApprovals || {};
    this.client.redirectUris = this.client.redirectUris || [];
    this.client.scopes =  this.client.scopes || [];
    this.initGrantTypes();
    this.initResponseTypes();
    this.initCustomGrantTypes();
    this.initScopes();
  }

  update() {
    this.client.authorizedGrantTypes = this.selectedGrantTypes.concat(this.selectedCustomGrantTypes);
    this.client.responseTypes = this.selectedResponseTypes;
    this.client.scopes = _.map(this.selectedScopes, scope => scope.key);
    this.client.scopeApprovals = this.selectedScopeApprovals;
    this.clientService.update(this.domainId, this.client.id, this.client).subscribe(data => {
      this.client = data;
      this.snackbarService.open('Client updated');
      this.formChanged = false;
      this.initGrantTypes();
      this.initResponseTypes();
      this.initCustomGrantTypes();
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
    this.customGrantTypes.forEach(customGrantType => {
      customGrantType.value = customGrantType.grantType;
      customGrantType.checked = _.some(this.client.authorizedGrantTypes, authorizedGrantType => authorizedGrantType.toLowerCase() === customGrantType.value.toLowerCase());
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

  selectedGrantType(event) {
    this.grantTypes
      .filter(grantType => grantType.value === event.source.value)
      .map(grantType => grantType.checked = event.checked);
    this.updateResponseTypeAccordingGrantType(event);
    this.formChanged = true;
  }

  selectedCustomGrantType(event) {
    this.customGrantTypes
      .filter(grantType => grantType.value === event.source.value)
      .map(grantType => grantType.checked = event.checked);
    this.formChanged = true;
  }

  selectedResponseType(event) {
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

}
