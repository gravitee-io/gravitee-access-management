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
import { Component, OnInit, ViewChild, ViewContainerRef } from '@angular/core';
import { ClientService } from "../../../../services/client.service";
import { SnackbarService } from "../../../../services/snackbar.service";
import { ProviderService } from "../../../../services/provider.service";
import { ActivatedRoute, Router } from "@angular/router";
import { CertificateService } from "../../../../services/certificate.service";
import { DialogService } from "../../../../services/dialog.service";

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
  @ViewChild('dynamic', { read: ViewContainerRef }) viewContainerRef: ViewContainerRef;
  private domainId: string;
  domain: any;
  client: any;
  formChanged: boolean = false;
  identityProviders: any[] = [];
  certificates: any[] = [];

  constructor(private clientService: ClientService,
              private snackbarService: SnackbarService,
              private providerService: ProviderService,
              private certificateService: CertificateService,
              private route: ActivatedRoute,
              private router: Router,
              private dialogService: DialogService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainId = this.domain.id;
    this.client = this.route.snapshot.parent.data['client'];
    this.providerService.findByDomain(this.domainId).subscribe(data => this.identityProviders = data);
    this.certificateService.findByDomain(this.domainId).subscribe(data => this.certificates = data);
  }

  enableClient(event) {
    this.client.enabled = event.checked;
    this.formChanged = true;
  }

  enableAutoApprove(event) {
    this.client.autoApproveScopes = (event.checked) ? ["true"]: [];
    this.formChanged = true;
  }

  isDomainDcrTemplateDisabled() {
    return !this.domain.oidc.clientRegistrationSettings.isClientTemplateEnabled;
  }

  isAutoApprove() {
    return this.client.autoApproveScopes && this.client.autoApproveScopes.indexOf('true') > -1;
  }

  update() {
    this.clientService.update(this.domainId, this.client.id, this.client).subscribe(data => {
      this.client = data;
      this.formChanged = false;
      this.snackbarService.open("Client updated");
    });
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

  renewClientSecret(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Renew Client secret', 'Are you sure you want to renew the client secret ?')
      .subscribe(res => {
        if (res) {
          this.clientService.renewClientSecret(this.domainId, this.client.id).subscribe(data => {
            this.client = data;
            this.snackbarService.open("Client secret updated");
          });
        }
      });
  }
}
