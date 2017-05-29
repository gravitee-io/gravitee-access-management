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
import { BreadcrumbService } from "ng2-breadcrumb/bundles/components/breadcrumbService";

@Component({
  selector: 'app-provider',
  templateUrl: './provider.component.html',
  styleUrls: ['./provider.component.scss']
})
export class ProviderComponent implements OnInit {
  private domainId: string;
  provider: any;
  navLinks: any = [{'href': 'settings' , 'label': 'Settings'}, {'href': 'mappers' , 'label': 'Mappers'}];

  constructor(private route: ActivatedRoute, private breadcrumbService: BreadcrumbService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.params['domainId'];
    this.provider = this.route.snapshot.data['provider'];
    this.initBreadcrumb();
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/providers/'+this.provider.id+'$', this.provider.name);
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/providers/'+this.provider.id+'/mappers$', 'mappers');
  }
}
