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
import { Component, EventEmitter, OnInit, Output, ViewChild } from '@angular/core';
import { ActivatedRoute } from "@angular/router";
import { SnackbarService } from "../../../../services/snackbar.service";
import { ClientService } from "../../../../services/client.service";
import { NgForm} from "@angular/forms";

@Component({
  selector: 'app-oidc',
  templateUrl: './oidc.component.html',
  styleUrls: ['./oidc.component.scss']
})
export class ClientOIDCComponent implements OnInit {
  private domainId: string;
  client: any;
  claims: any = [];
  editing = {};
  formChanged: boolean = false;

  constructor(private route: ActivatedRoute,
              private clientService: ClientService,
              private snackbarService: SnackbarService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.client = this.route.snapshot.parent.data['client'];
    this.setClaims(this.client.idTokenCustomClaims);
  }

  setClaims(claims) {
    for (var k in claims){
      if (claims.hasOwnProperty(k)) {
        this.claims.push({
          key: k,
          value: claims[k]
        });
      }
    }
  }

  addClaim(claim) {
    if (claim) {
      if (!this.claimExits(claim.key)) {
        this.claims.push(claim);
        this.claims = [...this.claims];
        this.client.idTokenCustomClaims = this.claims.reduce(function(map, obj) { map[obj.key] = obj.value; return map; }, {});
        this.formChanged = true;
      } else {
        this.snackbarService.open(`Error : claim ${claim.key} already exists`);
      }
    }
  }

  updateClaim(event, cell, rowIndex) {
    let claim = event.target.value;
    if (claim) {
      if (cell === 'key' && this.claimExits(claim)) {
        this.snackbarService.open(`Error : claim ${claim} already exists`);
        return;
      }
      this.editing[rowIndex + '-' + cell] = false;
      this.claims[rowIndex][cell] = claim;
      this.claims = [...this.claims];
      this.client.idTokenCustomClaims = this.claims.reduce(function(map, obj) { map[obj.key] = obj.value; return map; }, {});
      this.formChanged = true;
    }
  }

  update(message: string) {
    this.clientService.update(this.domainId, this.client.id, this.client).subscribe(data => {
      this.client = data;
      this.snackbarService.open(message);
      this.formChanged = false;
    });
  }

  delete(key, event) {
    event.preventDefault();
    this.claims = this.claims.filter(function(el) { return el.key !== key; });
    this.client.idTokenCustomClaims = this.claims.reduce(function(map, obj) { map[obj.key] = obj.value; return map; }, {});
    this.formChanged = true;
  }

  claimExits(attribute): boolean {
    return this.claims.map(function(m) { return m.key; }).indexOf(attribute) > -1;
  }

  get isEmpty() {
    return !this.claims || this.claims.length == 0;
  }
}

@Component({
  selector: 'app-create-claim',
  templateUrl: './claims/add-claim.component.html'
})
export class CreateClaimComponent {
  claim: any = {};
  @Output() addClaimChange = new EventEmitter();
  @ViewChild('claimForm') form: NgForm;

  constructor() {}

  addClaim() {
    this.addClaimChange.emit(this.claim);
    this.claim = {};
    this.form.reset(this.claim);
  }
}
