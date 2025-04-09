import { Component, OnInit, inject } from '@angular/core';
import { FlowFilterService } from '../flow-filter.service';
import { FlowPairingService, IndexedEntry } from '../flow-pairing.service';
import { ModelDiffDataService } from '../model-diff-data.service';
import { Entry } from '../types';
import { IconEmbedService } from '../icon-embed.service';

@Component({
  selector: 'app-unpaired-flow-list',
  templateUrl: './unpaired-flow-list.component.html',
  styleUrls: ['./unpaired-flow-list.component.css'],
})
export class UnpairedFlowListComponent implements OnInit {
  private mdds = inject(ModelDiffDataService);
  private fps = inject(FlowPairingService);
  private filter = inject(FlowFilterService);
  private icons = inject(IconEmbedService);


  leftEntries: Entry[] = [];
  rightEntries: Entry[] = [];

  constructor() {
    const fps = this.fps;
    const filter = this.filter;
    const icons = this.icons;


    // We've got a whole new dataset!
    fps.onRebuild(() => this.rebuild());

    // it'd be nice if the filters were just a visual thing, but I don't
    // know how to maintain a dragged order in the face of things being
    // filtered in and out
    filter.onUpdate(() => this.rebuild());

    fps.onUnpair((pair) => {
      this.leftEntries.push(pair.left.entry);
      this.rightEntries.push(pair.right.entry)
    });
    fps.onPair((pair) => {
      this.leftEntries = this.leftEntries.filter(e => e.detail !== pair.left.entry.detail);
      this.rightEntries = this.rightEntries.filter(e => e.detail !== pair.right.entry.detail);
    });
    icons.register("link");
  }

  ngOnInit(): void {
  }

  private rebuild(): void {
    this.leftEntries = Array.from(this.fps.unpairedLeftEntries())
      .map(ie => ie.entry)
      .filter(e => this.filter.passes(e));
    this.rightEntries = Array.from(this.fps.unpairedRightEntries())
      .map(ie => ie.entry)
      .filter(e => this.filter.passes(e));
  }

  leftBasePath(): string {
    return this.mdds.index("from")?.path ?? "";
  }

  rightBasePath(): string {
    return this.mdds.index("to")?.path ?? "";
  }

  links(): any[] {
    return this.leftEntries.length < this.rightEntries.length
      ? this.leftEntries
      : this.rightEntries;
  }

  pair(pi: number): void {

    let left: IndexedEntry | null = this.fps.unpairedLeftEntries()
      .find(ie => ie.entry.detail === this.leftEntries[pi].detail) ?? null;
    let right: IndexedEntry | null = this.fps.unpairedRightEntries()
      .find(ie => ie.entry.detail === this.rightEntries[pi].detail) ?? null;

    if (left === null) {
      console.error("Failed to find left ", this.leftEntries[pi]);
    }
    else if (right == null) {
      console.error("Failed to find right ", this.rightEntries[pi]);
    }
    else {
      this.fps.pair(left, right);
    }
  }
}
