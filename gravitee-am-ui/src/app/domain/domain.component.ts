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
import {ActivatedRoute, Router} from "@angular/router";
import {SidenavService} from "../components/sidenav/sidenav.service";
import {BreadcrumbService} from "../../libraries/ng2-breadcrumb/components/breadcrumbService";
import {DomainService} from "../services/domain.service";
import {NavbarService} from "../components/navbar/navbar.service";
import {SnackbarService} from "../services/snackbar.service";
import {AuthService} from "../services/auth.service";

@Component({
  selector: 'app-domain',
  templateUrl: './domain.component.html',
  styleUrls: ['./domain.component.scss']
})
export class DomainComponent implements OnInit {
  domain: any = {};

  constructor(private route: ActivatedRoute,
              private router: Router,
              private sidenavService: SidenavService,
              private navbarService: NavbarService,
              private breadcrumbService: BreadcrumbService,
              private snackbarService: SnackbarService,
              private domainService: DomainService,
              private authService: AuthService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainService.domainUpdated$.subscribe(domain => this.domain = domain);
    setTimeout(() => {
      this.navbarService.notify(this.domain);
    });
    this.initBreadcrumb();
    // redirect user according to its permissions
    if (this.router.url.indexOf('applications') === -1 && this.router.url.indexOf('settings') === -1) {
      if (this.canNavigate(['domain_analytics_read'])) {
        this.router.navigate(['/domains', this.domain.id, 'dashboard']);
      } else {
        this.router.navigate(['/domains', this.domain.id, 'applications']);
      }
    } else {
      this.router.navigateByUrl(this.router.url);
    }
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRoute('/domains/' + this.domain.id, this.domain.name);
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domain.id + '/settings/(providers|certificates|roles)/.*', ' ');
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/' + this.domain.id + '/settings/(providers|certificates|roles)/new$', 'new');
    this.breadcrumbService.hideRoute('/domains');
    this.breadcrumbService.hideRouteRegex('/domains/' + this.domain.id + '/settings/providers/.*/settings$');
    this.breadcrumbService.hideRouteRegex('/domains/' + this.domain.id + '/clients/.*/settings$');
  }

  enable() {
    this.domain.enabled = true;
    this.domainService.enable(this.domain.id, this.domain).subscribe(response => {
      this.domain = response;
      this.snackbarService.open('Domain ' + this.domain.name + ' enabled');
    });
  }

  private canNavigate(permissions): boolean {
    return this.authService.hasPermissions(permissions);
  }
}

