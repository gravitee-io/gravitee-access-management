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
import { Component, OnDestroy } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Subscription } from 'rxjs';

import { AuthService } from '../../../../services/auth.service';

@Component({
  selector: 'app-domain-mcp-server-advanced',
  templateUrl: './advanced.component.html',
  standalone: false,
})
export class DomainMcpServerAdvancedComponent implements OnDestroy {
  private subscription: Subscription;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService,
  ) {
    this.subscription = this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe((next: NavigationEnd) => {
      if (next.url.endsWith('settings')) {
        this.loadPermissions();
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private loadPermissions(): void {
    if (this.canNavigate(['protected_resource_read'])) {
      this.router.navigate(['general'], { relativeTo: this.route });
    }
  }

  private canNavigate(permissions): boolean {
    return this.authService.hasPermissions(permissions);
  }
}
