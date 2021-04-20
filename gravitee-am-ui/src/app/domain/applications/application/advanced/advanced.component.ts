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
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterEvent } from '@angular/router';
import { AuthService } from '../../../../services/auth.service';
import { filter } from 'rxjs/operators';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-application-advanced',
  templateUrl: './advanced.component.html',
  styleUrls: ['./advanced.component.scss'],
})
export class ApplicationAdvancedComponent implements OnInit, OnDestroy {
  private subscription: Subscription;

  constructor(private router: Router, private route: ActivatedRoute, private authService: AuthService) {
    this.subscription = this.router.events.pipe(filter((event: RouterEvent) => event instanceof NavigationEnd)).subscribe((next) => {
      if (next.url.endsWith('settings')) {
        this.loadPermissions();
      }
    });
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private loadPermissions(): void {
    const domainId = this.route.snapshot.parent.parent.params.domainId;
    const appId = this.route.snapshot.parent.params.appId;
    if (this.canNavigate(['application_settings_read'])) {
      this.router.navigate(['/domains', domainId, 'applications', appId, 'settings', 'general']);
    } else if (this.canNavigate(['application_oauth_read'])) {
      this.router.navigate(['/domains', domainId, 'applications', appId, 'settings', 'oauth2']);
    } else if (this.canNavigate(['application_member_list'])) {
      this.router.navigate(['/domains', domainId, 'applications', appId, 'settings', 'members']);
    } else if (this.canNavigate(['application_certificate_read'])) {
      this.router.navigate(['/domains', domainId, 'applications', appId, 'settings', 'certificates']);
    } else if (this.canNavigate(['application_factor_read'])) {
      this.router.navigate(['/domains', domainId, 'applications', appId, 'settings', 'factors']);
    }
  }

  private canNavigate(permissions): boolean {
    return this.authService.hasPermissions(permissions);
  }
}
