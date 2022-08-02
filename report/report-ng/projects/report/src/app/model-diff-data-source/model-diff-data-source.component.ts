import { HttpClient } from '@angular/common/http';
import { Component, Input, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ProgressBarMode } from '@angular/material/progress-bar';
import { ActivatedRoute } from '@angular/router';
import { debounceTime, distinctUntilChanged, tap } from 'rxjs/operators';
import { IndexDataService } from '../index-data.service';
import { ModelDiffDataService } from '../model-diff-data.service';
import { empty_index, Index, isIndex } from '../types';

@Component({
  selector: 'app-model-diff-data-source',
  templateUrl: './model-diff-data-source.component.html',
  styleUrls: ['./model-diff-data-source.component.css']
})
export class ModelDiffDataSourceComponent implements OnInit {

  readonly start = "// START_JSON_DATA";
  readonly end = "// END_JSON_DATA";

  @Input() label = "";
  @Input() input = new FormControl('');

  linkText: string = "";
  progressState: ProgressBarMode = 'query';
  progressValue: number = 0;

  constructor(
    private mdds: ModelDiffDataService,
    private route: ActivatedRoute) {
  }

  ngOnInit(): void {

    this.mdds.onFlow(this.label, (l, idx) => {
      this.progressValue = this.mdds.flowLoadProgress(l);
    });

    this.mdds.onIndex(this.label, l => {
      this.progressValue = 0;
      let indexData = this.mdds.index(l);
      if (indexData === null) {
        // pending
        this.progressState = 'query';
        this.linkText = "Loading...";
      }
      else if (indexData.index === null) {
        // failed
        this.progressState = 'determinate';
        this.linkText = indexData.status;
      }
      else {
        // success!
        this.progressState = 'determinate';
        let idx = indexData.index;
        this.linkText = idx.meta.modelTitle
          + " @ " + new Date(idx.meta.timestamp).toLocaleString()
          + " | " + idx.entries.length + " flows";
      }
    });
    this.input.valueChanges.pipe(
      debounceTime(500),
      distinctUntilChanged())
      .subscribe(value => this.mdds.path(this.label, value));

    this.route.queryParamMap.subscribe(qp => {
      let raw = qp.get(this.label) ?? '';
      let decoded = decodeURIComponent(raw);
      this.input.setValue(decoded);
    });
  }

}
