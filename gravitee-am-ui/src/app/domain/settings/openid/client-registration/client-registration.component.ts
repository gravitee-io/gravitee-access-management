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
import {Component, OnInit} from '@angular/core';
import { ActivatedRoute } from "@angular/router";
import { BreadcrumbService } from "../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";

@Component({
  selector: 'app-openid-client-registration',
  templateUrl: './client-registration.component.html',
  styleUrls: ['./client-registration.component.scss']
})
export class DomainSettingsOpenidClientRegistrationComponent implements OnInit {
  private domainId: string;
  navLinks: any = [
    {'href': 'settings' , 'label': 'Settings'},
    {'href': 'default-scope' , 'label': 'Default Scopes'},
    {'href': 'allowed-scope' , 'label': 'Allowed Scopes'}
  ];

  formChanged: boolean = false;
  domain: any = {};

  constructor(private route: ActivatedRoute, private breadcrumbService: BreadcrumbService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.data['domain'].name;
    this.initBreadcrumb();
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/openid/clientRegistration/settings', 'settings');
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/openid/clientRegistration/default-scope', 'default scopes');
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/openid/clientRegistration/allowed-scope', 'allowed scopes');
  }
}
