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
import { ProviderService } from "../../../services/provider.service";
import { SnackbarService } from "../../../services/snackbar.service";
import { PlatformService } from "../../../services/platform.service";

@Component({
  selector: 'app-settings-management-general',
  templateUrl: './general.component.html',
  styleUrls: ['./general.component.scss']
})
export class ManagementGeneralComponent implements OnInit {
  settings: any;
  identityProviders: any[] = [];
  oauth2IdentityProviders: any[] = [];

  constructor(private providerService: ProviderService,
              private platformService: PlatformService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute) { }

  ngOnInit() {
    this.settings = this.route.snapshot.data['settings'];
    this.platformService.identityProviders().subscribe(data => {
      this.identityProviders = data.filter(idp => !idp.external);
      this.oauth2IdentityProviders = data.filter(idp => idp.external);
    })
  }

  update() {
    const settings = {};
    settings['identities'] = this.settings.identities;
    this.platformService.patchSettings(settings).subscribe(response => {
      this.settings = response;
      this.snackbarService.open('Settings updated');
    });
  }

}
