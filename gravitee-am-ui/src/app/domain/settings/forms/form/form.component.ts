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
import { Component, OnInit } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { BreadcrumbService } from "../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";
import { AppConfig } from "../../../../../config/app.config";
import {AuthService} from "../../../../services/auth.service";

@Component({
  selector: 'app-domain-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss']
})
export class DomainSettingsFormComponent implements OnInit {
  private domainId: string;
  template: string;
  rawTemplate: string;
  createMode: boolean;
  editMode: boolean;
  deleteMode: boolean;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private breadcrumbService: BreadcrumbService,
              private authService: AuthService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
      this.rawTemplate = 'LOGIN';
    } else {
      this.rawTemplate = this.route.snapshot.queryParams['template'];
    }
    this.createMode = this.authService.isAdmin() || this.authService.hasPermissions(['domain_form_create']);
    this.editMode = this.authService.isAdmin() || this.authService.hasPermissions(['domain_form_update']);
    this.deleteMode = this.authService.isAdmin() || this.authService.hasPermissions(['domain_form_delete']);
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.template = this.rawTemplate.toLowerCase().replace(/_/g, ' ');
    this.initBreadcrumb();
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domainId + '/settings/forms/form*', this.template);
  }
}
