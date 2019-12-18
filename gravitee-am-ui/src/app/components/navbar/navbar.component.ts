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
import { AuthService } from "../../services/auth.service";
import { DomainService } from "../../services/domain.service";
import { Router } from "@angular/router";

@Component({
  selector: 'gs-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss']
})
export class NavbarComponent implements OnInit {
  domains: any[];

  constructor(private authService: AuthService, private domainService: DomainService, private router:Router) { }

  ngOnInit() {
    if (!this.authService.user()) {
      this.authService.userInfo().subscribe();
    }
  }

  get user() {
    return this.authService.user() != null ? this.authService.user().preferred_username : null;
  }

  isAuthenticated() {
    return this.authService.isAuthenticated();
  }

  listDomains() {
    this.domainService.list().subscribe(data => this.domains = data);
  }

  goTo(routerLink) {
    // needed to trick reuse route strategy, skipLocationChange to avoid /dummy to go into history
    this.router.navigateByUrl('/dummy', { skipLocationChange: true })
      .then(() => this.router.navigate(routerLink));
  }
}
