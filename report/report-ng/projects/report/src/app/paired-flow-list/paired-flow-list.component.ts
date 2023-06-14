import { Component, OnInit } from '@angular/core';
import { FlowFilterService } from '../flow-filter.service';
import { FlowPairingService, Pair } from '../flow-pairing.service';
import { ModelDiffDataService } from '../model-diff-data.service';
import { Entry } from '../types';
import { IconEmbedService } from '../icon-embed.service';

@Component({
  selector: 'app-paired-flow-list',
  templateUrl: './paired-flow-list.component.html',
  styleUrls: ['./paired-flow-list.component.css']
})
export class PairedFlowListComponent implements OnInit {

  constructor(
    private mdds: ModelDiffDataService,
    private fps: FlowPairingService,
    private filter: FlowFilterService,
    private icons: IconEmbedService,) {
    icons.register("link_off");
  }

  ngOnInit(): void {
  }

  leftBasePath(): string {
    return this.mdds.index("from")?.path ?? "";
  }

  rightBasePath(): string {
    return this.mdds.index("to")?.path ?? "";
  }

  private filtered(): Pair[] {
    return this.fps.paired()
      .filter(pair =>
        this.filter.passes(pair.left.entry)
        || this.filter.passes(pair.right.entry));
  }

  left(): Entry[] {
    return this.filtered()
      .map(e => e.left.entry);
  }

  right(): Entry[] {
    return this.filtered()
      .map(e => e.right.entry);
  }

  unpair(pi: number): void {
    let toUnpair = this.filtered()[pi];
    this.fps.unpair(toUnpair);
  }
}
