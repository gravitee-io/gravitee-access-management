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
import {ActivatedRoute} from "@angular/router";
import {BreadcrumbService} from "../../../../libraries/ng2-breadcrumb/components/breadcrumbService";
import * as _ from 'lodash';
import {AuthService} from "../../../services/auth.service";

@Component({
  selector: 'app-application',
  templateUrl: './application.component.html',
  styleUrls: ['./application.component.scss']
})
export class ApplicationComponent implements OnInit {
  private domainId: string;
  application: any;
  logoUrl: string;
  navLinks: any = [
    {'href': 'overview' , 'label': 'Overview', 'icon': 'more_vert'},
    {'href': 'idp' , 'label': 'Identity Providers', 'icon': 'swap_horiz'},
    {'href': 'design' , 'label': 'Design', 'icon': 'palette'},
    {'href': 'settings', 'label': 'Settings', 'icon': 'settings'}
  ];

  constructor(private route: ActivatedRoute,
              private breadcrumbService: BreadcrumbService,
              private authService: AuthService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.params['domainId'];
    this.application = this.route.snapshot.data['application'];
    this.logoUrl = 'assets/application-type-icons/' + this.application.type.toLowerCase() + '.png';
    if (this.application.type === 'service') {
      _.remove(this.navLinks, { href: 'idp' });
      _.remove(this.navLinks, { href: 'design' });
    }
    if (!this.canDisplay(['application_identity_provider_read'])) {
      _.remove(this.navLinks, { href: 'idp' });
    }
    if (!this.canDisplay(['application_email_template_read']) && !this.canDisplay(['application_form_read'])) {
      _.remove(this.navLinks, { href: 'design' });
    }
    if (!this.canDisplay(['application_metadata_read'])
            && !this.canDisplay(['application_oauth_read'])
            && !this.canDisplay(['application_user_account_read'])
            && !this.canDisplay(['application_certificate_read'])
          ) {
      _.remove(this.navLinks, { href: 'settings' });
    }
    setTimeout(() => this.initBreadcrumb());
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domainId + '/applications/' + this.application.id + '$', this.application.name);
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domainId + '/applications/' + this.application.id + '/idp$', 'IdP');
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domainId + '/applications/' + this.application.id + '/design', 'Design');
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domainId + '/applications/' + this.application.id + '/design/forms', 'Forms');
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domainId + '/applications/' + this.application.id + '/design/emails', 'Emails');
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domainId + '/applications/' + this.application.id + '/advanced', 'Advanced');
  }

  private canDisplay(permissions): boolean {
    return this.authService.isAdmin() || this.authService.hasPermissions(permissions);
  }
}
