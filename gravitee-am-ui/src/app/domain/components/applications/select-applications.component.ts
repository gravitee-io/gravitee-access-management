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
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from "@angular/router";
import { FormControl } from "@angular/forms";
import { ApplicationService } from "../../../services/application.service";

@Component({
  selector: 'app-select-applications',
  templateUrl: './select-applications.component.html'
})
export class SelectApplicationsComponent implements OnInit {
  private domainId: string;
  appCtrl = new FormControl();
  filteredApps: any[];
  @Input() selectedApp: any;
  @Output() onSelectApp = new EventEmitter<any>();
  @Output() onRemoveApp = new EventEmitter<any>();

  constructor(private route: ActivatedRoute,
              private applicationService: ApplicationService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.appCtrl.valueChanges
      .subscribe(searchTerm => {
        if (typeof(searchTerm) === 'string' || searchTerm instanceof String) {
          this.applicationService.search(this.domainId, searchTerm + '*').subscribe(response => {
            this.filteredApps = response.data;
          });
        }
      });

    if (this.selectedApp) {
      this.appCtrl.setValue({name: this.selectedApp.name, clientId: this.selectedApp.clientId});
    }
  }

  onAppSelectionChanged(event) {
    this.selectedApp = event.option.value;
    this.onSelectApp.emit(this.selectedApp);
  }

  displayFn(app?: any): string | undefined {
    return app ? app.name : undefined;
  }

  removeApp(event) {
    event.preventDefault();
    this.onRemoveApp.emit(this.selectedApp);
    this.selectedApp = null;
    this.appCtrl.setValue('');
  }
}
