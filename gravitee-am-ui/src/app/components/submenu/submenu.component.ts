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
import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { NavigationService } from '../../services/navigation.service';

@Component({
  selector: 'gv-submenu',
  templateUrl: './submenu.component.html',
  styleUrls: ['./submenu.component.scss'],
})
export class SubmenuComponent implements OnInit, OnDestroy {
  @Input('displaySections') displaySections = true;
  @Input('hasTitle') hasTitle = false;
  @Input('isTemplate') isTemplate = false;
  @Input('templateTooltip') templateTooltip: string;
  @Input('componentType') componentType: string;
  @Input('componentName') componentName: string;
  @Input('theme') theme: string;
  @Input('hasGoBackButton') hasGoBackButton = false;
  @Input('reduced') reduced = true;
  @Input('static') static = false;
  subMenuItems: any;
  subscription: Subscription;

  constructor(private router: Router, private navigationService: NavigationService) {}

  ngOnInit(): void {
    this.subscription = this.navigationService.level2MenuItemsObs$.subscribe(
      (items) =>
        (this.subMenuItems = items.reduce((r, a) => {
          r[a.section] = r[a.section] || [];
          r[a.section].push(a);
          return r;
        }, {})),
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  goBack(): void {
    this.router.navigate([''] );
  }
}

@Component({
  selector: 'gv-submenu-items',
  template: `<ng-container *ngFor="let link of items">
    <a title="{{ link.label }}" [routerLink]="link.path" routerLinkActive #rla="routerLinkActive">
      <gio-submenu-item [active]="rla.isActive">{{ link.label }}</gio-submenu-item>
    </a>
  </ng-container> `,
})
export class SubmenuItemsComponent {
  @Input('items') items: any[];
}
