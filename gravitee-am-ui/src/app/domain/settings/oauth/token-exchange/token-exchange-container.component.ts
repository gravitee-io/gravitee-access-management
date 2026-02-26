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
import { Component } from '@angular/core';

interface NavLink {
  readonly href: string;
  readonly label: string;
}

@Component({
  selector: 'app-token-exchange-container',
  templateUrl: './token-exchange-container.component.html',
  styleUrls: ['./token-exchange-container.component.scss'],
  standalone: false,
})
export class TokenExchangeContainerComponent {
  navLinks: NavLink[] = [
    { href: 'settings', label: 'Settings' },
    { href: 'trusted-issuers', label: 'Trusted Issuers' },
  ];
}
