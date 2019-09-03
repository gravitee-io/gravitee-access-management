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
import { ClientService } from "../../../../services/client.service";
import { SnackbarService } from "../../../../services/snackbar.service";
import { ActivatedRoute } from "@angular/router";
import { ProviderService } from "../../../../services/provider.service";

@Component({
  selector: 'app-idp',
  templateUrl: './idp.component.html',
  styleUrls: ['./idp.component.scss']
})
export class ClientIdPComponent implements OnInit {
  private domainId: string;
  loadIdentities: boolean = true;
  client: any;
  identityProviders: any[];
  socialIdentityProviders: any[];

  constructor(private route: ActivatedRoute,
              private clientService: ClientService,
              private snackbarService: SnackbarService,
              private providerService: ProviderService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.client = this.route.snapshot.parent.data['client'];
    this.providerService.findByDomain(this.domainId).subscribe(data => {
      this.identityProviders = data.filter(idp => !idp.external);
      this.socialIdentityProviders = data.filter(idp => idp.external);
      this.loadIdentities = false;
    });
  }

  update() {
    this.clientService.update(this.domainId, this.client.id, this.client).subscribe(data => {
      this.client = data;
      this.snackbarService.open("Client updated");
    });
  }

  selectIdentityProvider(event, identityProviderId) {
    if (this.client.identities === undefined) {
      this.client.identities = [];
    }
    (event.checked) ? this.client.identities.push(identityProviderId) :  this.client.identities.splice(this.client.identities.indexOf(identityProviderId), 1);
    this.update();
  }

  isIdentityProviderSelected(identityProviderId) {
    return this.client.identities !== undefined && this.client.identities.includes(identityProviderId);
  }

  hasIdentityProviders() {
    return this.identityProviders && this.identityProviders.length > 0;
  }

  hasSocialIdentityProviders() {
    return this.socialIdentityProviders && this.socialIdentityProviders.length > 0;
  }
}
