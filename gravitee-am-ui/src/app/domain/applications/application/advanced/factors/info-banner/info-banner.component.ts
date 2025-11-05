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
import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';
@Component({
  selector: 'info-banner',
  templateUrl: './info-banner.component.html',
  styleUrls: ['./info-banner.component.scss'],
  standalone: false,
})
export class InfoBannerComponent {
  @Input() link: string;
  @Input() text: string;
  @Input() type: 'warning' | 'info' | undefined;
  @Input() buttonName = 'More info';
  @Input() isRouterLink = true;
  @Input() showButton = true;

  constructor(private router: Router) {}
  getColors(): string {
    if (this.type === 'warning') {
      return `background-color: #FFECE5; border: 3px solid #BF3F0E; color: #BF3F0E;`;
    } else {
      return `background-color: #E7E2FB; border: 3px solid #51438E; color: #51438E;`;
    }
  }
  gotoLink($event: Event): void {
    $event.preventDefault();
    if (this.isRouterLink) {
      this.router.navigate([this.link]);
    } else {
      window.open(this.link, '_blank');
    }
  }
}
