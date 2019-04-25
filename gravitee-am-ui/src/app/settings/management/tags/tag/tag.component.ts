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
import {Component, OnInit, ViewChild} from "@angular/core";
import {ActivatedRoute, Router} from "@angular/router";
import {SnackbarService} from "../../../../services/snackbar.service";
import {BreadcrumbService} from "../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";
import {MatInput} from "@angular/material/input";
import {TagService} from "../../../../services/tag.service";
import {NgForm} from "@angular/forms";

export interface Tag {
  id: string;
  name: string;
  description: string;
}

@Component({
  selector: 'app-tag',
  templateUrl: './tag.component.html',
  styleUrls: ['./tag.component.scss']
})
export class TagComponent implements OnInit {

  tag: any;
  @ViewChild('tagForm') public tagForm: NgForm;

  constructor(private tagService: TagService, private snackbarService: SnackbarService, private route: ActivatedRoute,
              private router: Router, private breadcrumbService: BreadcrumbService) { }

  ngOnInit() {
    this.tag = this.route.snapshot.data['tag'];

    this.initBreadcrumb();
  }

  update() {
    this.tagService.update(this.tag.id, this.tag).map(res => res.json()).subscribe(data => {
      this.tag = data;
      this.initBreadcrumb();
      this.tagForm.reset(Object.assign({}, this.tag));
      this.snackbarService.open("Sharding tag updated");
    });
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/settings/management/tags/'+this.tag.id+'$', this.tag.name);
  }
}
