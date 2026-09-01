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

import { AuthService } from '../../../services/auth.service';

interface NavLink {
  readonly href: string;
  readonly label: string;
}

@Component({
  selector: 'app-trust-domains-container',
  templateUrl: './trust-domains-container.component.html',
  standalone: false,
})
export class TrustDomainsContainerComponent implements OnInit {
  navLinks: NavLink[] = [];

  constructor(private authService: AuthService) {}

  ngOnInit() {
    this.navLinks = [{ href: 'domains', label: 'Domains' }];
    if (this.authService.hasPermissions(['domain_settings_read'])) {
      this.navLinks.push({ href: 'key-retrieval', label: 'Key retrieval' });
    }
  }
}
