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
import { UntypedFormControl, Validators } from '@angular/forms';
import { map, mergeMap, startWith, tap } from 'rxjs/operators';
import { Observable } from 'rxjs';

import { ApplicationService } from '../../../services/application.service';

@Component({
  selector: 'app-select-template-application',
  templateUrl: './select-template-application.component.html',
  standalone: false,
})
export class SelectTemplateApplicationComponent implements OnInit, OnChanges {
  private domainId: string;
  appCtrl = new UntypedFormControl();
  filteredApps: Observable<any>;
  hasNoTemplateApps = false;
  selectedApp: any = null;
  @Input() selectedAppId: string;
  @Input() disabled: boolean;
  @Input() required = false;
  @Output() appSelected = new EventEmitter<string>();
  @Output() appCleared = new EventEmitter<void>();

  constructor(
    private route: ActivatedRoute,
    private applicationService: ApplicationService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;

    if (this.required) {
      this.appCtrl.setValidators(Validators.required);
    }

    if (this.disabled) {
      this.appCtrl.disable();
    }

    this.filteredApps = this.appCtrl.valueChanges.pipe(
      startWith(''),
      mergeMap((value) => {
        const searchTerm = typeof value === 'string' || value instanceof String ? value + '*' : '*';
        return this.applicationService.search(this.domainId, searchTerm);
      }),
      map((value) => value['data'].filter((app) => app.template)),
      tap((apps) => (this.hasNoTemplateApps = apps.length === 0)),
    );

    if (this.selectedAppId) {
      this.applicationService.get(this.domainId, this.selectedAppId).subscribe((app) => {
        this.selectedApp = app;
        this.appCtrl.setValue(app, { emitEvent: false });
      });
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['disabled'] && !changes['disabled'].firstChange) {
      if (this.disabled) {
        this.appCtrl.disable();
      } else {
        this.appCtrl.enable();
      }
    }
  }

  onAppSelectionChanged(event) {
    this.selectedApp = event.option.value;
    this.appSelected.emit(this.selectedApp.id);
  }

  displayFn(app?: any): string | undefined {
    return app ? app.name : undefined;
  }

  clearApp(event) {
    event.preventDefault();
    this.selectedApp = null;
    this.appCtrl.setValue('');
    this.appCleared.emit();
  }
}
