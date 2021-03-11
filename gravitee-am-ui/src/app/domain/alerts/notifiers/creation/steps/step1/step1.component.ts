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
import {ActivatedRoute} from "@angular/router";

@Component({
  selector: 'alert-notifier-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss']
})
export class DomainAlertNotifierCreationStep1Component implements OnInit {
  notifiers: any[];
  @Input('alertNotifier') alertNotifier;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.notifiers = this.route.snapshot.data['notifiers'];
  }

  selectNotifier(notifier) {
    this.alertNotifier.type = notifier.id;
  }

  getNotifierIcon(notifier) {
    if (notifier && notifier.icon) {
      return `<img mat-card-avatar src="${notifier.icon}" alt="${notifier.name} image" title="${notifier.name}"/>`;
    }
    return `<i class="material-icons">notifications</i>`;
  }
}
