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
import { BreadcrumbService } from '../../../../../services/breadcrumb.service';
import { DomainService } from '../../../../../services/domain.service';
import { DialogService } from '../../../../../services/dialog.service';
import * as _ from 'lodash';

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
  configurationIsValid: boolean = true;
  configurationPristine: boolean = true;
  providerSchema: any;
  provider: any;
  providerConfiguration: any;
  updateProviderConfiguration: any;

  constructor(private providerService: ProviderService,
              private organizationService: OrganizationService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router,
              private breadcrumbService: BreadcrumbService,
              private domainService: DomainService,
              private dialogService: DialogService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.parent.params['domainId'];
    this.certificates = this.route.snapshot.data['certificates'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
    if (this.organizationContext) {
      this.organizationService.settings().subscribe(data => this.domain = data);
    } else {
      this.domainService.get(this.domainId).subscribe(data => this.domain = data);
    }
    this.provider = this.route.snapshot.parent.data['provider'];
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
      if (this.organizationContext) {
        this.breadcrumbService.addFriendlyNameForRouteRegex('/settings/management/providers/' + this.provider.id + '$', this.provider.name);
      } else {
        this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domainId + '/providers/' + this.provider.id + '$', this.provider.name);
      }
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
            if (this.organizationContext) {
              this.router.navigate(['/settings', 'management', 'providers']);
            } else {
              this.router.navigate(['/domains', this.domainId, 'settings', 'providers']);
            }
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
}
