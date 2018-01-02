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
import { BreadcrumbService } from "../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";
import { PlatformService } from "../../../../services/platform.service";
import { SnackbarService } from "../../../../services/snackbar.service";
import { ExtensionGrantService } from "../../../../services/extension-grant.service";

@Component({
  selector: 'app-extension-grant',
  templateUrl: './extension-grant.component.html',
  styleUrls: ['./extension-grant.component.scss']
})
export class ExtensionGrantComponent implements OnInit {
  private domainId: string;
  formChanged: boolean = false;
  configurationIsValid: boolean = true;
  configurationPristine: boolean = true;
  extensionGrant: any;
  extensionGrantSchema: any;
  extensionGrantConfiguration: any;
  updateTokenGranterConfiguration: any;
  identityProviders: any[];
  rfc3986_absolute_URI = /^[A-Za-z][A-Za-z0-9+\-.]*:(?:\/\/(?:(?:[A-Za-z0-9\-._~!$&'()*+,;=:]|%[0-9A-Fa-f]{2})*@)?(?:\[(?:(?:(?:(?:[0-9A-Fa-f]{1,4}:){6}|::(?:[0-9A-Fa-f]{1,4}:){5}|(?:[0-9A-Fa-f]{1,4})?::(?:[0-9A-Fa-f]{1,4}:){4}|(?:(?:[0-9A-Fa-f]{1,4}:){0,1}[0-9A-Fa-f]{1,4})?::(?:[0-9A-Fa-f]{1,4}:){3}|(?:(?:[0-9A-Fa-f]{1,4}:){0,2}[0-9A-Fa-f]{1,4})?::(?:[0-9A-Fa-f]{1,4}:){2}|(?:(?:[0-9A-Fa-f]{1,4}:){0,3}[0-9A-Fa-f]{1,4})?::[0-9A-Fa-f]{1,4}:|(?:(?:[0-9A-Fa-f]{1,4}:){0,4}[0-9A-Fa-f]{1,4})?::)(?:[0-9A-Fa-f]{1,4}:[0-9A-Fa-f]{1,4}|(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?))|(?:(?:[0-9A-Fa-f]{1,4}:){0,5}[0-9A-Fa-f]{1,4})?::[0-9A-Fa-f]{1,4}|(?:(?:[0-9A-Fa-f]{1,4}:){0,6}[0-9A-Fa-f]{1,4})?::)|[Vv][0-9A-Fa-f]+\.[A-Za-z0-9\-._~!$&'()*+,;=:]+)\]|(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|(?:[A-Za-z0-9\-._~!$&'()*+,;=]|%[0-9A-Fa-f]{2})*)(?::[0-9]*)?(?:\/(?:[A-Za-z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})*)*|\/(?:(?:[A-Za-z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})+(?:\/(?:[A-Za-z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})*)*)?|(?:[A-Za-z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})+(?:\/(?:[A-Za-z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})*)*|)(?:\?(?:[A-Za-z0-9\-._~!$&'()*+,;=:@\/?]|%[0-9A-Fa-f]{2})*)?$/;

  constructor(private route: ActivatedRoute, private breadcrumbService: BreadcrumbService, private platformService: PlatformService,
              private extensionGrantService: ExtensionGrantService, private snackbarService: SnackbarService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.extensionGrant = this.route.snapshot.data['extensionGrant'];
    this.identityProviders = this.route.snapshot.data['identityProviders'];
    this.extensionGrantConfiguration = JSON.parse(this.extensionGrant.configuration);
    this.updateTokenGranterConfiguration = this.extensionGrantConfiguration;
    this.platformService.extensionGrantSchema(this.extensionGrant.type).map(res => res.json()).subscribe(data => this.extensionGrantSchema = data);
    this.initBreadcrumb();
  }

  update() {
    this.extensionGrant.configuration = JSON.stringify(this.updateTokenGranterConfiguration);
    this.extensionGrantService.update(this.domainId, this.extensionGrant.id, this.extensionGrant).map(res => res.json()).subscribe(data => {
      this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/extensionGrants/'+this.extensionGrant.id+'$', this.extensionGrant.name);
      this.snackbarService.open("Extension grant updated");
    })
  }

  enableTokenGranterUpdate(configurationWrapper) {
    window.setTimeout(() => {
      this.configurationPristine = this.extensionGrant.configuration === JSON.stringify(configurationWrapper.configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updateTokenGranterConfiguration = configurationWrapper.configuration;
    });
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/extensionGrants/'+this.extensionGrant.id+'$', this.extensionGrant.name);
  }

  enableCreateUser(event) {
    this.extensionGrant.createUser = event.checked;
    this.formChanged = true;
  }

}
