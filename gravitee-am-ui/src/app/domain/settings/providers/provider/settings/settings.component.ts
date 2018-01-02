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
import { ProviderService } from "../../../../../services/provider.service";
import { SnackbarService } from "../../../../../services/snackbar.service";
import { ActivatedRoute } from "@angular/router";
import { PlatformService } from "../../../../../services/platform.service";
import { BreadcrumbService } from "../../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";

@Component({
  selector: 'provider-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class ProviderSettingsComponent implements OnInit {
  private domainId: string;
  configurationIsValid: boolean = true;
  configurationPristine: boolean = true;
  providerSchema: any;
  provider: any;
  providerConfiguration: any;
  updateProviderConfiguration: any;

  constructor(private providerService: ProviderService, private platformService: PlatformService,
              private snackbarService: SnackbarService, private route: ActivatedRoute, private breadcrumbService: BreadcrumbService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.parent.params['domainId'];
    this.provider = this.route.snapshot.parent.data['provider'];
    this.providerConfiguration = JSON.parse(this.provider.configuration);
    this.updateProviderConfiguration = this.providerConfiguration;
    this.platformService.identitySchema(this.provider.type).map(res => res.json()).subscribe(data => {
      this.providerSchema = data;
      // handle default null values
      let self = this;
      Object.keys(this.providerSchema['properties']).forEach(function(key) {
        self.providerSchema['properties'][key].default = '';
      });
    });
  }

  update() {
    this.provider.configuration = JSON.stringify(this.updateProviderConfiguration);
    this.providerService.update(this.domainId, this.provider.id, this.provider).map(res => res.json()).subscribe(data => {
      this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/providers/'+this.provider.id+'$', this.provider.name);
      this.snackbarService.open("Provider updated");
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
