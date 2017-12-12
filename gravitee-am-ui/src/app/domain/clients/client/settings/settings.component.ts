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
import { Component, OnInit, ViewChild } from '@angular/core';
import { ClientService } from "../../../../services/client.service";
import { SnackbarService } from "../../../../services/snackbar.service";
import { ProviderService } from "../../../../services/provider.service";
import { ActivatedRoute, Router } from "@angular/router";
import { CertificateService } from "../../../../services/certificate.service";
import { DialogService } from "../../../../services/dialog.service";
import { MatInput } from "@angular/material/input";
import * as _ from 'lodash';

export interface Scope {
  id: string;
  key: string;
  name: string;
}

@Component({
  selector: 'client-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class ClientSettingsComponent implements OnInit {

  @ViewChild('chipInput') chipInput: MatInput;

  private domainId: string;
  private selectedScopes: Scope[];
  client: any;
  formChanged: boolean = false;
  identityProviders: any[] = [];
  certificates: any[] = [];
  scopes: any[] = [];
  grantTypes: any[] = [
    { name:'CLIENT CREDENTIALS', value:'client_credentials', checked:false },
    { name:'PASSWORD', value:'password', checked:false },
    { name:'AUTHORIZATION CODE', value:'authorization_code', checked:false },
    { name:'REFRESH TOKEN', value:'refresh_token', checked:false },
    { name:'IMPLICIT', value:'implicit', checked:false }
  ];
  customGrantTypes: any[];

  constructor(private clientService: ClientService, private snackbarService: SnackbarService,
              private providerService: ProviderService, private certificateService: CertificateService,
              private route: ActivatedRoute, private router: Router, private dialogService: DialogService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.client = this.route.snapshot.parent.data['client'];
    this.customGrantTypes = this.route.snapshot.data['domainGrantTypes'];
    this.scopes = this.route.snapshot.data['scopes'];
    (!this.client.redirectUris) ? this.client.redirectUris = [] : this.client.redirectUris = this.client.redirectUris;
    (!this.client.scopes) ? this.client.scopes = [] : this.client.scopes = this.client.scopes;
    this.providerService.findByDomain(this.domainId).map(res => res.json()).subscribe(data => this.identityProviders = data);
    this.certificateService.findByDomain(this.domainId).map(res => res.json()).subscribe(data => this.certificates = data);
    this.initGrantTypes();
    this.initScopes();
    this.initCustomGrantTypes();
  }

  initGrantTypes() {
    this.grantTypes.forEach(grantType => {
      grantType.checked = _.some(this.client.authorizedGrantTypes, authorizedGrantType => authorizedGrantType.toLowerCase() === grantType.value.toLowerCase());
    })
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

  initCustomGrantTypes() {
    this.customGrantTypes.forEach(customGrantType => {
      customGrantType.value = customGrantType.grantType;
      customGrantType.checked = _.some(this.client.authorizedGrantTypes, authorizedGrantType => authorizedGrantType.toLowerCase() === customGrantType.value.toLowerCase());
    });
  }

  selectedGrantType(event) {
    this.grantTypes
      .filter(grantType => grantType.value === event.source.value)
      .map(grantType => grantType.checked = event.checked);
    this.formChanged = true;
  }

  selectedScope(event) {
    this.scopes
      .filter(scope => scope.key === event.source.value)
      .map(scope => scope.checked = event.checked);
    this.formChanged = true;
  }

  selectedCustomGrantType(event) {
    this.customGrantTypes
      .filter(grantType => grantType.value === event.source.value)
      .map(grantType => grantType.checked = event.checked);
    this.formChanged = true;
  }

  enableClient(event) {
    this.client.enabled = event.checked;
    this.formChanged = true;
  }

  enableAutoApprove(event) {
    this.client.autoApproveScopes = (event.checked) ? ["true"]: [];
    this.formChanged = true;
  }

  isAutoApprove() {
    return this.client.autoApproveScopes && this.client.autoApproveScopes.indexOf('true') > -1;
  }

  addScope(event) {
    this.selectedScopes = this.selectedScopes.concat(_.remove(this.scopes, { 'key': event.option.value }));
    this.chipInput['nativeElement'].blur();
    this.formChanged = true;
  }

  removeScope(scope) {
    this.scopes = this.scopes.concat(_.remove(this.selectedScopes, function(selectPermission) {
      return selectPermission.key === scope.key;
    }));

    this.chipInput['nativeElement'].blur();
    this.formChanged = true;
  }

  enhanceScopesWithUserPermissions(event) {
    this.client.enhanceScopesWithUserPermissions = event.checked;
    this.formChanged = true;
  }

  isScopesEnhanceWithUserPermissions() {
    return this.client.enhanceScopesWithUserPermissions;
  }

  enableMultipeTokens(event) {
    this.client.generateNewTokenPerRequest = event.checked;
    this.formChanged = true;
  }

  update() {
    this.client.authorizedGrantTypes = this.selectedGrantTypes.concat(this.selectedCustomGrantTypes);
    this.client.scopes = _.map(this.selectedScopes, scope => scope.key);
    this.clientService.update(this.domainId, this.client.id, this.client).map(res => res.json()).subscribe(data => {
      this.client = data;
      this.snackbarService.open("Client updated");
    });
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

  /*
  get selectedScopes() {
    return this.scopes
      .filter(scope => scope.checked)
      .map(scope => scope.key)
  }
  */

  addRedirectURI(event, input: HTMLInputElement) {
    this.addElement(input, this.client.redirectUris, event);
  }

  removeRedirectURI(redirectURI, event) {
    this.removeElement(redirectURI, this.client.redirectUris, event);
  }

  /*
  addScope(input: HTMLInputElement, event) {
    this.addElement(input, this.client.scopes, event);
  }

  removeScope(scope, event) {
    this.removeElement(scope, this.client.scopes, event);
  }
  */

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

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Client', 'Are you sure you want to delete this client ?')
      .subscribe(res => {
        if (res) {
          this.clientService.delete(this.domainId, this.client.id).subscribe(response => {
            this.snackbarService.open("Client deleted");
            this.router.navigate(['/domains', this.domainId, 'clients']);
          });
        }
      });
  }
}
