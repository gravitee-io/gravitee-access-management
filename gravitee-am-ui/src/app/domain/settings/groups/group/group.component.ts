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
import { BreadcrumbService } from "../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";
import { ActivatedRoute, Router } from "@angular/router";
import { AppConfig } from "../../../../../config/app.config";

@Component({
  selector: 'app-group',
  templateUrl: './group.component.html',
  styleUrls: ['./group.component.scss']
})
export class GroupComponent implements OnInit {
  private domainId: string;
  group: any;
  navLinks: any = [{'href': 'settings' , 'label': 'Settings'},
    {'href': 'members' , 'label': 'Members'}];


  constructor(private route: ActivatedRoute,
              private router: Router,
              private breadcrumbService: BreadcrumbService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
    }
    this.group = this.route.snapshot.data['group'];
    this.initBreadcrumb();
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/groups/'+this.group.id+'$', this.group.name);
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/groups/'+this.group.id+'/members', 'Members');
  }
}
