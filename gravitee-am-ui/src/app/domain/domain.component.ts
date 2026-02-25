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

import { DomainService } from '../services/domain.service';
import { SnackbarService } from '../services/snackbar.service';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-domain',
  templateUrl: './domain.component.html',
  styleUrls: ['./domain.component.scss'],
  standalone: false,
})
export class DomainComponent implements OnInit {
  domain: any = {};
  environment: any = {};

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private snackbarService: SnackbarService,
    private domainService: DomainService,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    this.environment = this.route.snapshot.data['environment'];
    this.domain = this.route.snapshot.data['domain'];
    this.domainService.domainUpdated$.subscribe((domain) => (this.domain = domain));

    // redirect user according to its permissions
    if (
      this.router.url.indexOf('applications') === -1 &&
      this.router.url.indexOf('settings') === -1 &&
      this.router.url.indexOf('mcp-servers') === -1 &&
      this.router.url.indexOf('authorization') === -1
    ) {
      if (this.canNavigate(['domain_analytics_read'])) {
        this.router.navigate(['dashboard'], { relativeTo: this.route });
      } else if (this.canNavigate(['domain_authorization_engine_list'])) {
        this.router.navigate(['authorization'], { relativeTo: this.route });
      } else if (this.canNavigate(['application_list'])) {
        this.router.navigate(['applications'], { relativeTo: this.route });
      } else if (this.canNavigate(['protected_resource_list'])) {
        this.router.navigate(['mcp-servers'], { relativeTo: this.route });
      } else {
        this.router.navigate(['settings'], { relativeTo: this.route });
      }
    } else {
      this.router.navigateByUrl(this.router.url);
    }
  }

  enable() {
    this.domain.enabled = true;
    this.domainService.enable(this.domain.id, this.domain).subscribe((response) => {
      this.domain = response;
      this.snackbarService.open('Domain ' + this.domain.name + ' enabled');
    });
  }

  hasPermissions(permissions): boolean {
    return this.authService.hasPermissions(permissions);
  }

  private canNavigate(permissions): boolean {
    return this.authService.hasPermissions(permissions);
  }
}
