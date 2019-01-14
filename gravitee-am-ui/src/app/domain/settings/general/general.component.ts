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
import { Component, OnInit } from '@angular/core';
import { DomainService } from "../../../services/domain.service";
import { DialogService } from "../../../services/dialog.service";
import { ActivatedRoute, Router } from "@angular/router";
import { SnackbarService } from "../../../services/snackbar.service";
import { BreadcrumbService } from "../../../../libraries/ng2-breadcrumb/components/breadcrumbService";
import { SidenavService } from "../../../components/sidenav/sidenav.service";

@Component({
  selector: 'app-general',
  templateUrl: './general.component.html',
  styleUrls: ['./general.component.scss']
})
export class DomainSettingsGeneralComponent implements OnInit {
  formChanged: boolean = false;
  domain: any = {};

  constructor(private domainService: DomainService, private dialogService: DialogService, private snackbarService: SnackbarService,
              private router: Router, private route: ActivatedRoute, private breadcrumbService: BreadcrumbService, private sidenavService: SidenavService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.parent.data['domain'];
  }

  enableDomain(event) {
    this.domain.enabled = event.checked;
    this.formChanged = true;
  }

  update() {
    this.domainService.patchGeneralSettings(this.domain.id, this.domain).subscribe(response => {
      this.domain = response.json();
      this.domainService.notify(this.domain);
      this.sidenavService.notify(this.domain);
      this.breadcrumbService.addFriendlyNameForRoute('/domains/'+this.domain.id, this.domain.name);
      this.snackbarService.open("Domain " + this.domain.name + " updated");
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Domain', 'Are you sure you want to delete this domain ?')
      .subscribe(res => {
        if (res) {
          this.domainService.delete(this.domain.id).subscribe(response => {
            this.snackbarService.open("Domain " + this.domain.name + " deleted");
            this.router.navigate(['']);
          })
        }
      });
  }
}
