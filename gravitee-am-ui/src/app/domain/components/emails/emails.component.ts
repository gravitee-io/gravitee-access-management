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
import {Component, Input, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from "@angular/router";
import {AuthService} from "../../../services/auth.service";

@Component({
  selector: 'app-emails',
  templateUrl: './emails.component.html',
  styleUrls: ['./emails.component.scss']
})
export class EmailsComponent implements OnInit {
  appId: string;
  @Input() emails: any[];
  private viewPermission: String;

  constructor(private router: Router,
              private route: ActivatedRoute,
              private authService: AuthService) { }

  ngOnInit() {
    this.appId = this.route.snapshot.params['appId'];

    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.viewPermission = 'organization_form_read';
    } else if (this.appId) {
      this.viewPermission = 'application_form_read';
    } else {
      this.viewPermission = 'domain_form_read';
    }
  }

  isEmpty() {
    return !this.emails || this.emails.length == 0;
  }

  canView(): boolean {
    return this.authService.hasPermissions([this.viewPermission]);
  }

  getRowClass(row) {
    return {
      'row-disabled': !row.enabled
    };
  }
}
