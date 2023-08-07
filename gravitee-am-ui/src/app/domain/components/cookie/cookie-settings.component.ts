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
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-cookie-settings',
  templateUrl: './cookie-settings.component.html',
  styleUrls: ['./cookie-settings.component.scss'],
})
export class CookieSettingsComponent implements OnInit, OnChanges {
  @Output() onSavedCookieSettings = new EventEmitter<any>();
  @Input() cookieSettings: any;
  @Input() inheritMode = false;
  @Input() readonly = false;
  formChanged = false;
  private domainId: string;

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.cookieSettings.previousValue && changes.cookieSettings.currentValue) {
      this.cookieSettings = changes.cookieSettings.currentValue;
    }
  }

  save() {
    let cookieSettings = Object.assign({}, this.cookieSettings);
    if (cookieSettings.inherited) {
      cookieSettings = { inherited: true };
    } else {
      if (!cookieSettings.session) {
        cookieSettings.session = { persistent: false };
      }
    }
    this.onSavedCookieSettings.emit(cookieSettings);
    this.formChanged = false;
  }

  enableInheritMode(event) {
    this.cookieSettings.inherited = event.checked;
    this.formChanged = true;
  }

  isInherited() {
    return this.cookieSettings && this.cookieSettings.inherited;
  }

  isSessionPersistent() {
    return this.cookieSettings.session && this.cookieSettings.session.persistent;
  }

  enableSessionPersistent($event) {
    if (!this.cookieSettings.session) {
      this.cookieSettings.session = {};
    }
    this.cookieSettings.session.persistent = $event.checked;
    this.formChanged = true;
  }
}
