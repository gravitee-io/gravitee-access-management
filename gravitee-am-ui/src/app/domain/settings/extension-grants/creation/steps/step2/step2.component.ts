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
import {Component, Input, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from "@angular/router";
import {PlatformService} from "../../../../../../services/platform.service";
import {SnackbarService} from "../../../../../../services/snackbar.service";
import {ExtensionGrantService} from "../../../../../../services/extension-grant.service";

@Component({
  selector: 'extension-grant-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss']
})
export class ExtensionGrantCreationStep2Component implements OnInit {
  @Input('extensionGrant') extensionGrant: any;
  private domainId: string;
  formChanged: boolean = false;
  configuration: any;
  configurationIsValid: boolean = false;
  extensionGrantSchema: any = {};
  identityProviders: any[];
  rfc3986_absolute_URI = /^[A-Za-z][A-Za-z0-9+\-.]*:(?:\/\/(?:(?:[A-Za-z0-9\-._~!$&'()*+,;=:]|%[0-9A-Fa-f]{2})*@)?(?:\[(?:(?:(?:(?:[0-9A-Fa-f]{1,4}:){6}|::(?:[0-9A-Fa-f]{1,4}:){5}|(?:[0-9A-Fa-f]{1,4})?::(?:[0-9A-Fa-f]{1,4}:){4}|(?:(?:[0-9A-Fa-f]{1,4}:){0,1}[0-9A-Fa-f]{1,4})?::(?:[0-9A-Fa-f]{1,4}:){3}|(?:(?:[0-9A-Fa-f]{1,4}:){0,2}[0-9A-Fa-f]{1,4})?::(?:[0-9A-Fa-f]{1,4}:){2}|(?:(?:[0-9A-Fa-f]{1,4}:){0,3}[0-9A-Fa-f]{1,4})?::[0-9A-Fa-f]{1,4}:|(?:(?:[0-9A-Fa-f]{1,4}:){0,4}[0-9A-Fa-f]{1,4})?::)(?:[0-9A-Fa-f]{1,4}:[0-9A-Fa-f]{1,4}|(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?))|(?:(?:[0-9A-Fa-f]{1,4}:){0,5}[0-9A-Fa-f]{1,4})?::[0-9A-Fa-f]{1,4}|(?:(?:[0-9A-Fa-f]{1,4}:){0,6}[0-9A-Fa-f]{1,4})?::)|[Vv][0-9A-Fa-f]+\.[A-Za-z0-9\-._~!$&'()*+,;=:]+)\]|(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|(?:[A-Za-z0-9\-._~!$&'()*+,;=]|%[0-9A-Fa-f]{2})*)(?::[0-9]*)?(?:\/(?:[A-Za-z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})*)*|\/(?:(?:[A-Za-z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})+(?:\/(?:[A-Za-z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})*)*)?|(?:[A-Za-z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})+(?:\/(?:[A-Za-z0-9\-._~!$&'()*+,;=:@]|%[0-9A-Fa-f]{2})*)*|)(?:\?(?:[A-Za-z0-9\-._~!$&'()*+,;=:@\/?]|%[0-9A-Fa-f]{2})*)?$/;

  constructor(private platformService: PlatformService,
              private extensionGrantService: ExtensionGrantService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.identityProviders = this.route.snapshot.data['identityProviders'];
    this.platformService.extensionGrantSchema(this.extensionGrant.type).subscribe(data => {
      this.extensionGrantSchema = data;
      // set the grant_type value
      if (this.extensionGrantSchema.properties.grantType) {
        this.extensionGrant.grantType = this.extensionGrantSchema.properties.grantType.default;
      }
    });
  }

  enableTokenGranterCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.extensionGrant.configuration = configurationWrapper.configuration;
  }

  create() {
    this.extensionGrant.configuration = JSON.stringify(this.extensionGrant.configuration);
    this.extensionGrantService.create(this.domainId, this.extensionGrant).subscribe(data => {
      this.snackbarService.open("Extension Grant " + data.name + " created");
      this.router.navigate(['/domains', this.domainId, 'settings', 'extensionGrants', data.id]);
    });
  }

  enableCreateUser(event) {
    this.extensionGrant.createUser = event.checked;
    this.formChanged = true;
  }

  get isValid() {
    if (this.extensionGrant.name && this.configurationIsValid) {
      return true;
    } else {
      return false;
    }
  }
}
