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
import { ProviderService } from "../../../services/provider.service";
import { AppConfig } from "../../../../config/app.config";
import { ActivatedRoute } from "@angular/router";
import { DomainService } from "../../../services/domain.service";
import { SnackbarService } from "../../../services/snackbar.service";

@Component({
  selector: 'app-settings-management-general',
  templateUrl: './general.component.html',
  styleUrls: ['./general.component.scss']
})
export class ManagementGeneralComponent implements OnInit {
  private domainId: string = AppConfig.settings.authentication.domainId;
  domain: any = {};
  identityProviders: any[] = [];
  oauth2IdentityProviders: any[] = [];

  constructor(private providerService: ProviderService, private domainService: DomainService,
              private snackbarService: SnackbarService, private route: ActivatedRoute) { }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.providerService.findByDomain(this.domainId).map(res => res.json()).subscribe(data => {
      this.identityProviders = data.filter(idp => !idp.external);
      this.oauth2IdentityProviders = data.filter(idp => idp.external);
    });
  }

  update() {
    this.domainService.update(this.domain.id, this.domain).subscribe(response => {
      this.domain = response.json();
      this.snackbarService.open("Settings updated");
    });
  }

}
