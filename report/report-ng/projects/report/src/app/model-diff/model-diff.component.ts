import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { ChangeAnalysisComponent } from '../change-analysis/change-analysis.component';
import { ChangeViewComponent } from '../change-view/change-view.component';
import { FlowFilterService } from '../flow-filter.service';
import { FlowPairingService } from '../flow-pairing.service';
import { ModelDiffDataSourceComponent } from '../model-diff-data-source/model-diff-data-source.component';

@Component({
  selector: 'app-model-diff',
  templateUrl: './model-diff.component.html',
  styleUrls: ['./model-diff.component.css']
})
export class ModelDiffComponent implements OnInit {

  @ViewChild("from") from!: ModelDiffDataSourceComponent;
  @ViewChild("to") to!: ModelDiffDataSourceComponent;
  @ViewChild(ChangeViewComponent) changeView!: ChangeViewComponent;
  @ViewChild(ChangeAnalysisComponent) changeAnalysis!: ChangeAnalysisComponent;
  tabIndex = 0;

  constructor(
    private filters: FlowFilterService,
    private fps: FlowPairingService,
    private location: Location,
    route: ActivatedRoute,
    title: Title) {
    this.tabIndex = Number.parseInt(route.snapshot.queryParamMap.get("tab") ?? "0");
    filters.onUpdate(() => this.updateQuery());
    fps.onUnpair(p => this.updateQuery());
    fps.onPair(p => this.updateQuery());
    title.setTitle("Model diff");
  }

  ngOnInit(): void {
  }

  ngAfterViewInit() {
    this.from.input.valueChanges.subscribe(v => this.updateQuery());
    this.to.input.valueChanges.subscribe(v => this.updateQuery());
    this.changeView.onSelection(() => this.updateQuery());
    this.changeAnalysis.toChangeView((from, to) => {
      this.tabIndex = 2;
      this.changeView.view(from, to);
    });
  }

  swap(): void {
    let tmp: string = this.from.input.value;
    this.from.input.setValue(this.to.input.value);
    this.to.input.setValue(tmp);
  }

  updateQuery(): void {
    const usp: URLSearchParams = new URLSearchParams();
    if (this.from.input.value) {
      usp.append("from", encodeURIComponent(this.from.input.value));
    }
    if (this.to.input.value) {
      usp.append("to", encodeURIComponent(this.to.input.value));
    }
    if (this.tabIndex != 0) {
      usp.append("tab", this.tabIndex.toString());
    }
    this.filters.buildQuery(usp);
    this.fps.buildQuery(usp);
    this.changeView.buildQuery(usp);

    let query = usp.toString();

    let path = this.location.path();
    const i = path.indexOf('?');
    if (i >= 0) {
      path = path.substring(0, i);
    }
    this.location.replaceState(path, query);
  }

  hasPairs(): boolean {
    return this.fps.paired().length > 0;
  }

  hasUnpairs(): boolean {
    return this.fps.unpairedLeftEntries().length > 0
      || this.fps.unpairedRightEntries().length > 0;
  }
}
