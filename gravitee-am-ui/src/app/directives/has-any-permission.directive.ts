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
import {Directive, ElementRef, Input, TemplateRef, ViewContainerRef} from '@angular/core';
import {AuthService} from '../services/auth.service';
import {forEach} from "angular7-json-schema-form";

@Directive({
  selector: '[hasAnyPermission]'
})
export class HasAnyPermissionDirective {
  private permissions = [];

  constructor(private element: ElementRef,
              private templateRef: TemplateRef<any>,
              private viewContainer: ViewContainerRef,
              private authService: AuthService) {
  }

  @Input()
  set hasAnyPermission(val) {
    this.permissions = val;
    this.updateView();
  }

  private updateView() {

    if (this.authService.hasAnyPermissions(this.permissions) === true) {
      this.viewContainer.createEmbeddedView(this.templateRef);
    } else {
      this.viewContainer.clear();
    }
  }
}
