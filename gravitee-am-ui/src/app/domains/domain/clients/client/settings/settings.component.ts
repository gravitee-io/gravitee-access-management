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
import { Component, OnInit, Input } from '@angular/core';
import { ClientService } from "../../../../../services/client.service";
import { SnackbarService } from "../../../../../services/snackbar.service";
import { ProviderService } from "../../../../../services/provider.service";
import { ActivatedRoute } from "@angular/router";
import { CertificateService } from "../../../../../services/certificate.service";

@Component({
  selector: 'client-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class ClientSettingsComponent implements OnInit {
  private domainId: string;
  client: any;
  formChanged: boolean = false;
  identityProviders: any[] = [];
  certificates: any[] = [];
  grantTypes: any[] = [
    { name:'CLIENT CREDENTIALS', value:'client_credentials', checked:false },
    { name:'PASSWORD', value:'password', checked:false },
    { name:'AUTHORIZATION CODE', value:'authorization_code', checked:false },
    { name:'REFRESH TOKEN', value:'refresh_token', checked:false },
    { name:'IMPLICIT', value:'implicit', checked:false }
  ];

  constructor(private clientService: ClientService, private snackbarService: SnackbarService,
              private providerService: ProviderService, private certificateService: CertificateService, private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.client = this.route.snapshot.parent.data['client'];
    this.providerService.findByDomain(this.domainId).map(res => res.json()).subscribe(data => this.identityProviders = data);
    this.certificateService.findByDomain(this.domainId).map(res => res.json()).subscribe(data => this.certificates = data);
    this.initGrantTypes();
  }

  initGrantTypes() {
    (!this.client.redirectUris) ? this.client.redirectUris = [] : this.client.redirectUris = this.client.redirectUris;
    (!this.client.scopes) ? this.client.scopes = [] : this.client.scopes = this.client.scopes;
    let self = this;
    this.grantTypes.forEach(function (grantType) {
      if (self.contains(grantType.value, self.client.authorizedGrantTypes)) {
        grantType.checked = true;
      }
    })
  }

  selectedGrantType(event) {
    this.grantTypes
      .filter(grantType => grantType.value === event.source.value)
      .map(grantType => grantType.checked = event.checked);
    this.formChanged = true;
  }

  contains(obj, list) {
    let i;
    for (i = 0; i < list.length; i++) {
      if (list[i] === obj) {
        return true;
      }
    }
    return false;
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

  enhanceScopesWithUserPermissions(event) {
    this.client.enhanceScopesWithUserPermissions = event.checked;
    this.formChanged = true;
  }

  isScopesEnhanceWithUserPermissions() {
    return this.client.enhanceScopesWithUserPermissions;
  }

  update() {
    this.client.authorizedGrantTypes = this.selectedGrantTypes;
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

  addRedirectURI(event, input: HTMLInputElement) {
    this.addElement(input, this.client.redirectUris, event);
  }

  removeRedirectURI(redirectURI, event) {
    this.removeElement(redirectURI, this.client.redirectUris, event);
  }

  addScope(input: HTMLInputElement, event) {
    this.addElement(input, this.client.scopes, event);
  }

  removeScope(scope, event) {
    this.removeElement(scope, this.client.scopes, event);
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

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }
}
