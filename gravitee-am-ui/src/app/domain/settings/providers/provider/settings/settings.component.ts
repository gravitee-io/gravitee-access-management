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
import { NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ProviderService } from '../../../../../services/provider.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { OrganizationService } from '../../../../../services/organization.service';
import { DomainService } from '../../../../../services/domain.service';
import { DialogService } from '../../../../../services/dialog.service';
import * as _ from 'lodash';
import {AppConfig} from "../../../../../../config/app.config";
import {EntrypointService} from "../../../../../services/entrypoint.service";

@Component({
  selector: 'provider-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class ProviderSettingsComponent implements OnInit {
  @ViewChild('providerForm') public form: NgForm;
  private domainId: string;
  private certificates: any[];
  organizationContext = false;
  domain: any = {};
  entrypoint: any = {};
  configurationIsValid = true;
  configurationPristine = true;
  providerSchema: any;
  provider: any;
  providerConfiguration: any;
  updateProviderConfiguration: any;
  redirectUri: string;
  customCode: string;

  constructor(private providerService: ProviderService,
              private organizationService: OrganizationService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router,
              private domainService: DomainService,
              private dialogService: DialogService,
              private entrypointService: EntrypointService) { }

  ngOnInit() {
    this.provider = this.route.snapshot.data['provider'];
    this.certificates = this.route.snapshot.data['certificates'];
    this.customCode = '<a th:href="${authorizeUrls.get(\'' + this.provider.id + '\')}">SIGN IN WITH OAUTH2 PROVIDER</a>';
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
    if (this.organizationContext) {
      this.organizationService.settings().subscribe(data => this.domain = data);
      this.entrypoint = { url: AppConfig.settings.baseURL};
      this.redirectUri = this.entrypoint.url + '/auth/login/callback?provider=' + this.provider.id;
    } else {
      this.domainId = this.route.snapshot.params['domainId'];
      this.domain = this.route.snapshot.data['domain'];
      this.domainService.getEntrypoint(this.domainId).subscribe(data => {
        this.entrypoint = data;
        this.redirectUri = this.entrypointService.resolveBaseUrl(this.entrypoint, this.domain) + '/login/callback?provider=' + this.provider.id;
      });
    }
    this.providerConfiguration = JSON.parse(this.provider.configuration);
    this.updateProviderConfiguration = this.providerConfiguration;
    this.organizationService.identitySchema(this.provider.type).subscribe(data => {
      this.providerSchema = data;
      // handle default null values
      let self = this;
      Object.keys(this.providerSchema['properties']).forEach(function(key) {
        self.providerSchema['properties'][key].default = '';
      });
      // enhance schema information
      if (this.providerSchema.properties.graviteeCertificate && this.certificates && this.certificates.length > 0) {
        this.providerSchema.properties.graviteeCertificate.enum = _.flatMap(this.certificates, 'id');
        this.providerSchema.properties.graviteeCertificate['x-schema-form'] = { "type" : "select" };
        this.providerSchema.properties.graviteeCertificate['x-schema-form'].titleMap = this.certificates.reduce(function(map, obj) {
          map[obj.id] = obj.name;
          return map;
        }, {});
      }
    });
  }

  update() {
    this.provider.configuration = JSON.stringify(this.updateProviderConfiguration);
    this.providerService.update(this.domainId, this.provider.id, this.provider, this.organizationContext).subscribe(data => {
      this.snackbarService.open('Provider updated');
      this.configurationPristine = true;
      this.form.reset(data);
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Provider', 'Are you sure you want to delete this provider ?')
      .subscribe(res => {
        if (res) {
          this.providerService.delete(this.domainId, this.provider.id, this.organizationContext).subscribe(() => {
            this.snackbarService.open('Identity provider deleted');
            this.router.navigate(['../..'], { relativeTo: this.route });
          });
        }
      });
  }

  enableProviderUpdate(configurationWrapper) {
    window.setTimeout(() => {
      this.configurationPristine = this.provider.configuration === JSON.stringify(configurationWrapper.configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updateProviderConfiguration = configurationWrapper.configuration;
    });
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }
}
