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
import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {NavigationService} from "../../services/navigation.service";
import {Subscription} from "rxjs";

@Component({
  selector: 'gv-breadcrumb',
  templateUrl: './breadcrumb.component.html',
  styleUrls: ['./breadcrumb.component.scss']
})
export class BreadcrumbComponent implements OnInit, OnDestroy {
  private subscription: Subscription;
  breadcrumbItems : any[] = [];

  constructor(
    private navigationService : NavigationService
  ) {
  }

  ngOnInit() {
    this.subscription = this.navigationService.breadcrumbItemsObs$.subscribe(items => this.breadcrumbItems = items)
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
