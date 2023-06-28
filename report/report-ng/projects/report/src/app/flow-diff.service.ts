import { Injectable } from '@angular/core';
import { Diff, DIFF_EQUAL, diff_match_patch } from 'diff-match-patch';
import { FlowPairingService } from './flow-pairing.service';
import { ModelDiffDataService } from './model-diff-data.service';
import { Entry, Flow, Interaction, isResultTag } from './types';

/**
 * The source of diff data: a pair of flow datas
 */
export interface DiffPair {
  left: { entry: Entry, flow: Flow | null, flat: string } | null,
  right: { entry: Entry, flow: Flow | null, flat: string } | null,
};

/**
 * Associates a diff with a set of flow pairs that have undergone 
 * that diff
 */
export interface Change {
  diff: Diff,
  pairs: DiffPair[],
}

/**
 * Associates a set of diffs with a set of flows pairs that have
 * undergone those diffs
 */
export interface CollatedChange {
  diffs: Diff[],
  pairs: DiffPair[],
}

@Injectable({
  providedIn: 'root'
})
export class FlowDiffService {
  private readonly dmp: diff_match_patch = new diff_match_patch();

  sourceData: DiffPair[] = [];
  changes: Change[] = [];
  collated: CollatedChange[] = [];

  private rebuildListeners: (() => void)[] = [];
  private refreshListeners: (() => void)[] = [];

  constructor(
    private mdds: ModelDiffDataService,
    private fps: FlowPairingService,
  ) {
    fps.onRebuild(() => this.rebuild());

    fps.onPair(pair => {
      let left: DiffPair | null = this.sourceData.find(e =>
        e.left?.entry.detail === pair.left.entry.detail) ?? null;
      let right: DiffPair | null = this.sourceData.find(e =>
        e.right?.entry.detail === pair.right.entry.detail) ?? null;

      if (left === null) {
        console.error("Failed to find removed diffpair", pair.left, this.sourceData);
        this.rebuild();
      }
      else if (right === null) {
        console.error("Failed to find added diffpair", pair.right, this.sourceData);
        this.rebuild();
      }
      else {
        this.sourceData = this.sourceData.filter(e => e !== left && e !== right);
        let merged: DiffPair = { left: left.left, right: right.right };
        this.sourceData.push(merged);
        this.rebuildChanges();
        this.recollateChanges();
        this.rebuildListeners.forEach(cb => cb());
      }
    });
    fps.onUnpair(pair => {
      let split: DiffPair | null = this.sourceData.find(e =>
        (e.left?.entry.detail === pair.left.entry.detail)
        && (e.right?.entry.detail === pair.right.entry.detail)) ?? null;
      if (split === null) {
        console.error("Failed to find splitting pair", pair, this.sourceData);
        this.rebuild();
      }
      else {
        this.sourceData = this.sourceData.filter(e => e !== split);
        this.sourceData.push({ left: split.left, right: null });
        this.sourceData.push({ left: null, right: split.right });
        this.rebuildChanges();
        this.recollateChanges();
        this.rebuildListeners.forEach(cb => cb());
      }
    });

    let lastRefresh = Date.now();
    mdds.onFlow("from", (label, entry, flow) => {
      this.sourceData.forEach(dp => {
        if (dp.left !== null && dp.left.entry.detail === entry.detail) {
          dp.left.flow = flow;
          dp.left.flat = this.flatten("from", flow);
        }
      }
      );
      let delta = Date.now() - lastRefresh;
      if (mdds.flowLoadProgress("from") == 100 || delta > 3000) {
        this.rebuildChanges();
        this.recollateChanges();
        this.refreshListeners.forEach(cb => cb());
        lastRefresh = Date.now();
      }
    });
    mdds.onFlow("to", (label, entry, flow) => {
      this.sourceData.forEach(dp => {
        if (dp.right !== null && dp.right.entry.detail === entry.detail) {
          dp.right.flow = flow;
          dp.right.flat = this.flatten("to", flow);
        }
      }
      );
      let delta = Date.now() - lastRefresh;
      if (mdds.flowLoadProgress("to") == 100 || delta > 3000) {
        this.rebuildChanges();
        this.recollateChanges();
        this.refreshListeners.forEach(cb => cb());
        lastRefresh = Date.now();
      }
    });
  }

  onPairing(callback: () => void): void {
    this.rebuildListeners.push(callback);
  }

  onFlowData(callback: () => void): void {
    this.refreshListeners.push(callback);
  }

  rebuild(): void {
    this.rebuildSourceData();
    this.rebuildChanges();
    this.recollateChanges();
    this.rebuildListeners.forEach(cb => cb());
  }

  private rebuildSourceData(): void {

    let removed: DiffPair[] = this.fps.unpairedLeftEntries()
      .map(ie => ie.entry)
      .map(e => {
        let f = this.mdds.flowFor("from", e);
        return {
          left: { entry: e, flow: f, flat: this.flatten("from", f) },
          right: null
        };
      });

    let added: DiffPair[] = this.fps.unpairedRightEntries()
      .map(ie => ie.entry)
      .map(e => {
        let f = this.mdds.flowFor("to", e);
        return {
          left: null,
          right: { entry: e, flow: f, flat: this.flatten("to", f) }
        };
      });

    let changed: DiffPair[] = this.fps.naturallyPaired()
      .map(e => {
        let lf = this.mdds.flowFor("from", e.left.entry);
        let rf = this.mdds.flowFor("to", e.right.entry);
        return {
          left: { entry: e.left.entry, flow: lf, flat: this.flatten("from", lf) },
          right: { entry: e.right.entry, flow: rf, flat: this.flatten("to", rf) }
        };
      });

    let renamed: DiffPair[] = this.fps.manuallyPaired()
      .map(e => {
        let lf = this.mdds.flowFor("from", e.left.entry);
        let rf = this.mdds.flowFor("to", e.right.entry);
        return {
          left: { entry: e.left.entry, flow: lf, flat: this.flatten("from", lf) },
          right: { entry: e.right.entry, flow: rf, flat: this.flatten("to", rf) }
        };
      });

    this.sourceData = removed.concat(added, changed, renamed);
  }

  private flatten(label: string, flow: Flow | null): string {
    if (flow === null) {
      return "No data on the '" + label + "' side!";
    }
    let lines: string[] = [];
    lines.push("Identity:");
    lines.push("  " + flow.description);
    flow.tags
      .filter(t => !isResultTag(t))
      .forEach(t => lines.push("  " + t));
    lines.push("Motivation:");
    lines.push("  " + flow.motivation);
    lines.push("Context:");
    JSON.stringify(flow.context, null, 2)
      .split("\n").map(l => "  " + l)
      .forEach(l => lines.push(l));
    lines.push("Interactions:");
    this.flattenInteraction(flow.root, lines, "  ");
    return lines.join("\n");
  }

  private flattenInteraction(ntr: Interaction, lines: string[], indent: string): void {
    lines.push(indent + "┌REQUEST " + ntr.requester + " => " + ntr.responder + " [" + ntr.tags.join(",") + "]");
    ntr.request.full.expect.split("\n")
      .map(l => indent + "│" + l)
      .forEach(l => lines.push(l));

    if (ntr.children.length) {
      lines.push(indent + "╘ Provokes:");
      ntr.children.forEach(c => this.flattenInteraction(c, lines, indent + "  "));
    }
    else {
      lines.push(indent + "└");
    }

    lines.push(indent + "┌RESPONSE " + ntr.requester + " <= " + ntr.responder + " [" + ntr.tags.join(",") + "]");
    ntr.response.full.expect.split("\n")
      .map(l => indent + "│" + l)
      .forEach(l => lines.push(l));
    lines.push(indent + "└");
  }

  private rebuildChanges(): void {
    this.changes = [];
    this.sourceData
      .filter(pair => pair.left !== null && pair.right !== null)
      .forEach(pair => {
        let diffs: Diff[] = this.dmp.diff_main(
          pair.left?.flat ?? '',
          pair.right?.flat ?? '');

        diffs
          .filter(d => d[0] !== DIFF_EQUAL)
          .forEach(d => {
            let ec = this.changes
              .find(c => c.diff[0] === d[0] && c.diff[1] === d[1]);
            if (ec !== undefined) {
              if (ec.pairs.find(p => p === pair) === undefined) {
                ec.pairs.push(pair);
              }
            }
            else {
              this.changes.push({ diff: d, pairs: [pair] });
            }
          });
      });
  }

  private recollateChanges(): void {
    this.collated = [];
    this.changes.forEach(change => {
      let existing = this.collated.find(c => {
        let match = true;
        match = c.pairs.length === change.pairs.length;
        if (match) {
          c.pairs.forEach(p => {
            match = match && change.pairs.find(q => p === q) !== undefined;
          });
        }
        return match;
      });

      if (existing !== undefined) {
        existing.diffs.push(change.diff);
      }
      else {
        this.collated.push({ diffs: [change.diff], pairs: change.pairs });
      }
    });
  }
}
