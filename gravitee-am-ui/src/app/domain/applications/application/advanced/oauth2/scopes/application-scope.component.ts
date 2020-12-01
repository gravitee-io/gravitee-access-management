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
import {Component, EventEmitter, Input, Output, ViewChild} from '@angular/core'
import {FormControl, NgForm} from "@angular/forms";

@Component({
  selector: 'application-scope',
  templateUrl: './application-scope.component.html'
})

export class ApplicationScopeComponent {
  scope: any = {};
  selectScopes = new FormControl();
  @Output() addScopeChange = new EventEmitter();
  @Input() scopes: any[] = [];
  @ViewChild('applicationScopeForm', { static: true }) form: NgForm;

  constructor() {}

  addScope() {
    this.addScopeChange.emit(this.scope);
    this.scope = {};
    this.form.reset(this.scope);
  }

  formIsInvalid() {
    if (!this.scope.key) {
      return true;
    }

    if ((this.scope.expiresIn > 0 && !this.scope.unitTime) || (this.scope.expiresIn <= 0 && this.scope.unitTime)) {
      return true;
    }

    return false;
  }
}
