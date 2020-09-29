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
import {ActivatedRoute, NavigationEnd, Router, RouterEvent} from '@angular/router';
import {AuthService} from '../../../../services/auth.service';
import {filter} from 'rxjs/operators';
import {Subscription} from 'rxjs';

@Component({
  selector: 'app-application-design',
  templateUrl: './design.component.html',
  styleUrls: ['./design.component.scss']
})
export class ApplicationDesignComponent implements OnInit, OnDestroy {
  private subscription: Subscription;

  constructor(private authService: AuthService,
              private router: Router,
              private route: ActivatedRoute) {
    this.subscription = this.router.events.pipe(
      filter((event: RouterEvent) => event instanceof NavigationEnd)
    ).subscribe(next  => {
      if (next.url.endsWith('design')) {
        this.loadPermissions();
      }
    });
  }

  ngOnInit(): void {
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private loadPermissions(): void {
    if (this.canNavigate(['application_form_list', 'application_form_read'])) {
      this.router.navigate(['forms'], { relativeTo: this.route });
    } else if (this.canNavigate(['application_email_template_list', 'application_email_template_read'])) {
      this.router.navigate(['emails'], { relativeTo: this.route });
    }
  }

  private canNavigate(permissions): boolean {
    return this.authService.hasAnyPermissions(permissions);
  }
}
