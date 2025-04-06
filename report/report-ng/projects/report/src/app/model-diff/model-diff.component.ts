import { Location } from '@angular/common';
import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { ChangeAnalysisComponent } from '../change-analysis/change-analysis.component';
import { ChangeViewComponent } from '../change-view/change-view.component';
import { FlowFilterService } from '../flow-filter.service';
import { FlowPairingService } from '../flow-pairing.service';
import { ModelDiffDataSourceComponent } from '../model-diff-data-source/model-diff-data-source.component';
import { IconEmbedService } from '../icon-embed.service';

@Component({
  selector: 'app-model-diff',
  templateUrl: './model-diff.component.html',
  styleUrls: ['./model-diff.component.css']
})
export class ModelDiffComponent implements OnInit {
  private filters = inject(FlowFilterService);
  private fps = inject(FlowPairingService);
  private location = inject(Location);
  private icons = inject(IconEmbedService);


  @ViewChild("from") from!: ModelDiffDataSourceComponent;
  @ViewChild("to") to!: ModelDiffDataSourceComponent;
  @ViewChild(ChangeViewComponent) changeView!: ChangeViewComponent;
  @ViewChild(ChangeAnalysisComponent) changeAnalysis!: ChangeAnalysisComponent;
  tabIndex = 0;

  constructor() {
    const filters = this.filters;
    const fps = this.fps;
    const route = inject(ActivatedRoute);
    const title = inject(Title);
    const icons = this.icons;

    this.tabIndex = Number.parseInt(route.snapshot.queryParamMap.get("tab") ?? "0");
    filters.onUpdate(() => this.updateQuery());
    fps.onUnpair(p => this.updateQuery());
    fps.onPair(p => this.updateQuery());
    title.setTitle("Model diff");
    icons.register("swap_horiz")
  }

  ngOnInit(): void {
  }

  ngAfterViewInit() {
    this.from.valueChanges(v => this.updateQuery());
    this.to.valueChanges(v => this.updateQuery());
    this.changeView.onSelection(() => this.updateQuery());
    this.changeAnalysis.toChangeView((from, to) => {
      this.tabIndex = 2;
      this.changeView.view(from, to);
    });
  }

  swap(): void {
    let tmp: string = this.from.getValue();
    this.from.setValue(this.to.getValue());
    this.to.setValue(tmp);
  }

  updateQuery(): void {
    const usp: URLSearchParams = new URLSearchParams();
    if (this.from.getValue()) {
      usp.append("from", encodeURIComponent(this.from.getValue()));
    }
    if (this.to.getValue()) {
      usp.append("to", encodeURIComponent(this.to.getValue()));
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
