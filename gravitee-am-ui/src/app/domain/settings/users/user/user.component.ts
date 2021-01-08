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
import { ActivatedRoute, Router } from '@angular/router';
import { BreadcrumbService } from '../../../../services/breadcrumb.service';

interface NavLink {
  readonly href: string,
  readonly label: string
}

@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.scss'],
})
export class UserComponent implements OnInit {
  private domainId: string;
  private organizationContext = false;
  user: any;
  navLinks: NavLink[] = [];

  constructor(private route: ActivatedRoute,
              private router: Router,
              private breadcrumbService: BreadcrumbService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.user = this.route.snapshot.data['user'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
    this.initNavLinks();
    this.initBreadcrumb();
  }

  initNavLinks() {
    this.navLinks.push({ href: 'profile', label: 'Profile' });
    if (!this.organizationContext) {
      this.navLinks.push({ href: 'applications', label: 'Authorized Apps' });
      this.navLinks.push({ href: 'factors', label: 'Multi-Factor Authentication' });
      this.navLinks.push({ href: 'credentials', label: 'Credentials' });
      this.navLinks.push({ href: 'roles', label: 'Roles' });
      this.navLinks.push({ href: 'history', label: 'History' });
    }
  }

  initBreadcrumb() {
    if (this.organizationContext) {
      this.breadcrumbService.addFriendlyNameForRouteRegex('/settings/management/users/' + this.user.id + '$', this.user.username);
    } else {
      this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domainId + '/settings/users/' + this.user.id + '$', this.user.username);
      this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domainId + '/settings/users/' + this.user.id + '/applications$', 'Authorized Apps');
    }
  }
}
