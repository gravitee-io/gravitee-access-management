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
import {ActivatedRoute, Router} from '@angular/router';
import {AuthService} from '../../../../services/auth.service';

@Component({
  selector: 'app-application-design',
  templateUrl: './design.component.html',
  styleUrls: ['./design.component.scss']
})
export class ApplicationDesignComponent implements OnInit {

  constructor(private authService: AuthService,
              private router: Router,
              private route: ActivatedRoute) { }

  ngOnInit(): void {
    const domainId = this.route.snapshot.parent.parent.params['domainId'];
    const appId = this.route.snapshot.parent.params['appId'];
    if (this.canNavigate(['application_form_read'])) {
      this.router.navigate(['/domains', domainId, 'applications', appId, 'design', 'forms']);
    } else if (this.canNavigate(['application_email_template_read'])) {
      this.router.navigate(['/domains', domainId, 'applications', appId, 'design', 'emails']);
    }
  }

  private canNavigate(permissions): boolean {
    return this.authService.isAdmin() || this.authService.hasPermissions(permissions);
  }
}
