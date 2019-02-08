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
import { BreadcrumbService } from "../../../../libraries/ng2-breadcrumb/components/breadcrumbService";

@Component({
  selector: 'app-client',
  templateUrl: './client.component.html',
  styleUrls: ['./client.component.scss']
})
export class ClientComponent implements OnInit {
  private domainId: string;
  client: any;
  navLinks: any = [
    {'href': 'settings' , 'label': 'Settings'},
    {'href': 'idp' , 'label': 'Identity Providers'},
    {'href': 'oidc' , 'label': 'OpenID Connect'},
    {'href': 'emails' , 'label': 'Emails'}
  ];

  constructor(private route: ActivatedRoute, private breadcrumbService: BreadcrumbService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.params['domainId'];
    this.client = this.route.snapshot.data['client'];
    this.initBreadcrumb();
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/clients/'+this.client.id+'$', this.client.clientId);
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/clients/'+this.client.id+'/idp$', 'IdP');
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/clients/'+this.client.id+'/oidc$', 'OIDC');
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/clients/'+this.client.id+'/emails$', 'Emails');
  }
}
