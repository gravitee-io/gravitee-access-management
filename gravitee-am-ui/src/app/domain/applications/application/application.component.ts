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
import {Component, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, NavigationEnd, Router} from '@angular/router';
import {AuthService} from '../../../services/auth.service';
import * as _ from 'lodash';
import {filter} from "rxjs/operators";
import {Subject, Subscription} from "rxjs";

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
    {'href': 'endpoints' , 'label': 'Endpoints', 'icon': 'transform'},
    {'href': 'idp' , 'label': 'Identity Providers', 'icon': 'swap_horiz'},
    {'href': 'design' , 'label': 'Design', 'icon': 'palette'},
    {'href': 'settings', 'label': 'Settings', 'icon': 'settings'}
  ];

  constructor(private route: ActivatedRoute,
              private router: Router,
              private authService: AuthService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.application = this.route.snapshot.data['application'];
    this.logoUrl = 'assets/application-type-icons/' + this.application.type.toLowerCase() + '.png';

    if (this.application.type === 'service') {
      _.remove(this.navLinks, { href: 'idp' });
      _.remove(this.navLinks, { href: 'design' });
    }
    if (!this.canDisplay(['application_identity_provider_list'])) {
      _.remove(this.navLinks, { href: 'idp' });
    }
    if (!this.canDisplay(['application_email_template_list', 'application_email_template_read', 'application_form_list', 'application_form_read'])) {
      _.remove(this.navLinks, { href: 'design' });
    }
    if (!this.canDisplay(['application_settings_read'])
      && !this.canDisplay(['application_oauth_read'])
      && !this.canDisplay(['application_certificate_list'])
    ) {
      _.remove(this.navLinks, { href: 'settings' });
    }
  }

  private canDisplay(permissions): boolean {
    return this.authService.hasAnyPermissions(permissions);
  }
}
