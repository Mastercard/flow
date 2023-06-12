import { ViewportScroller } from '@angular/common';
import { Component, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { MatButtonToggleChange } from '@angular/material/button-toggle';
import { MatSelectionListChange } from '@angular/material/list';
import { ActivatedRoute } from '@angular/router';
import { DiffPair, FlowDiffService } from '../flow-diff.service';
import { FlowFilterService } from '../flow-filter.service';
import { ModelDiffDataService } from '../model-diff-data.service';
import { ListPair } from '../pair-select-item/pair-select-item.component';
import { DiffDisplay } from '../text-diff/text-diff.component';
import { IconEmbedService } from '../icon-embed.service';


@Component({
  selector: 'app-change-view',
  templateUrl: './change-view.component.html',
  styleUrls: ['./change-view.component.css'],
})
export class ChangeViewComponent implements OnInit {

  treeOpen: boolean = true;

  contextLines: number = 1;
  diffFormat: DiffDisplay = 'unified';

  selected: ListPair | null = null;
  private selectionListeners: (() => void)[] = [];
  blocks: { header: string, items: ListPair[] }[] = [];
  all: ListPair[] = [];
  noChangeMessage: string = "";

  diffLeft: string = "";
  diffRight: string = "";

  constructor(
    private fds: FlowDiffService,
    private mdds: ModelDiffDataService,
    private filter: FlowFilterService,
    private scroll: ViewportScroller,
    private route: ActivatedRoute,
    private icons: IconEmbedService,
  ) {
    filter.onUpdate(() => this.rebuild());
    fds.onPairing(() => this.rebuild());
    fds.onFlowData(() => this.rebuild());
    icons.register(
      "menu_open", "navigate_before", "navigate_next",
      "vertical_split", "horizontal_split", "expand_less");
  }

  ngOnInit(): void {
  }

  onSelection(cb: () => void): void {
    this.selectionListeners.push(cb);
  }

  private rebuild(): void {
    let passesFilters = (dp: DiffPair): boolean => {
      return (dp.left === null || this.filter.passes(dp.left.entry))
        || (dp.right === null || this.filter.passes(dp.right.entry));
    };
    let toUnindexedListPair = (dp: DiffPair): ListPair => {
      return { index: 0, pair: dp };
    };
    let removed: ListPair[] = [];
    let added: ListPair[] = [];
    let changed: ListPair[] = [];
    let renamed: ListPair[] = [];
    this.fds.sourceData
      .filter(passesFilters)
      .filter(dp => dp.left?.flat !== dp.right?.flat)
      .map(toUnindexedListPair)
      .forEach(uilp => {
        if (uilp.pair.right === null) {
          removed.push(uilp);
        } else if (uilp.pair.left === null) {
          added.push(uilp);
        }
        else if (uilp.pair.left.entry.detail === uilp.pair.right.entry.detail) {
          changed.push(uilp);
        }
        else {
          renamed.push(uilp);
        }
      });

    let index = 0;
    this.all = removed.concat(added, changed, renamed);
    this.all.forEach(p => p.index = index++);

    this.blocks = [];
    this.blocks.push({ header: "Removed", items: removed });
    this.blocks.push({ header: "Added", items: added });
    this.blocks.push({ header: "Changed", items: changed });
    this.blocks.push({ header: "Renamed", items: renamed });

    // our selection may have changed its index! find it in the new list
    this.view(this.route.snapshot.queryParamMap.get("ff"), this.route.snapshot.queryParamMap.get("tf"));

    this.noChangeMessage = this.filter.isEmpty()
      ? "No changes found!"
      : "Remove filters to view changes";

    this.refreshDiff();
  }

  view(from: string | null, to: string | null) {
    this.selected = this.all
      .find(lp => (lp.pair.left?.entry.detail ?? null) === from
        && (lp.pair.right?.entry.detail ?? null) === to)
      ?? null;
    if (this.selected === null && this.all.length > 0) {
      this.selected = this.all[0];
    }
  }

  buildQuery(usp: URLSearchParams): URLSearchParams {
    if (this.selected !== null && this.selected.pair.left !== null) {
      usp.append("ff", this.selected.pair.left.entry.detail);
    }
    if (this.selected !== null && this.selected.pair.right !== null) {
      usp.append("tf", this.selected.pair.right.entry.detail);
    }
    return usp;
  }

  refreshDiff(): void {
    this.diffLeft = "";
    if (this.selected != null && this.selected.pair.left != null) {
      this.diffLeft = this.selected.pair.left.flat;
    }
    this.diffRight = "";
    if (this.selected != null && this.selected.pair.right != null) {
      this.diffRight = this.selected.pair.right.flat;
    }
  }

  hasDiffData(): boolean {
    return this.selected !== null;
  }

  leftBasePath(): string {
    return this.mdds.index("from")?.path ?? "";
  }

  rightBasePath(): string {
    return this.mdds.index("to")?.path ?? "";
  }

  toggleTree(event: MouseEvent) {
    this.treeOpen = !this.treeOpen;
  }

  contextChange(event: MatButtonToggleChange) {
    let toggle = event.source;
    if (toggle) {
      let group = toggle.buttonToggleGroup;
      if (group.value === 'more') {
        this.contextLines *= 2;
        if (this.contextLines === 0) {
          this.contextLines = 1;
        }
      }
      else if (group.value === 'less') {
        this.contextLines /= 2;
        if (this.contextLines < 1) {
          this.contextLines = 0;
        }
      }

      group.value = "no_such_value";
    }
  }

  selectionChange(event: MatSelectionListChange) {
    this.selected = event.options[0].value;
    this.refreshDiff();
    this.selectionListeners.forEach(cb => cb());
  }

  canPrevious(): boolean {
    return this.selected !== null && this.selected.index > 0;
  }
  canNext(): boolean {
    return this.selected !== null && this.selected.index < this.all.length - 1;
  }

  navChange(event: MatButtonToggleChange) {
    let toggle = event.source;
    let desired = this.selected?.index ?? 0;
    if (toggle) {
      let group = toggle.buttonToggleGroup;
      if (group.value === 'previous') {
        desired--;
      }
      else if (group.value === 'next') {
        desired++;
      }
      else if (group.value === 'top') {
        this.scroll.scrollToPosition([0, 0]);
      }
      group.value = "no_such_value";
    }

    if (desired < 0) {
      desired = 0;
    }
    if (desired >= this.all.length) {
      desired = this.all.length - 1;
    }
    if (desired >= 0) {
      this.selected = this.all[desired];
    }
    else {
      this.selected = null;
    }
    this.refreshDiff();
    this.selectionListeners.forEach(cb => cb());
  }
}
