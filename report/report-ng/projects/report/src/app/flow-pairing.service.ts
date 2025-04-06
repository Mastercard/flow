import { Injectable, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ModelDiffDataService } from './model-diff-data.service';
import { Entry, isResultTag, removeResultTagsFrom } from './types';

@Injectable({
  providedIn: 'root'
})
export class FlowPairingService {
  private mdds = inject(ModelDiffDataService);
  private route = inject(ActivatedRoute);


  /**
   * The pairs of flows that we can match automatically - they have the same metadata
   */
  private automatic: Set<Pair> = new Set();
  /**
   * The pairs of flows we've had to add manually - metadata has changed.
   */
  private manualPairs: Set<Pair> = new Set();
  /**
   * The pairs of flows we've manually unpaired
   */
  private manualUnpairs: Set<Pair> = new Set();
  /**
   * Indices on the left that we haven't matched up yet.
   * These will show as deleted flows.
   */
  private leftUnpaired: Set<IndexedEntry> = new Set();
  /**
   * Indices on the right that we haven't matched up yet.
   * These will show as newly-added flows
   */
  private rightUnpaired: Set<IndexedEntry> = new Set();

  private unpairListeners: Set<((p: Pair) => void)> = new Set();
  private pairListeners: Set<((p: Pair) => void)> = new Set();
  private rebuildListeners: Set<(() => void)> = new Set();

  constructor() {
    const mdds = this.mdds;

    mdds.onIndex("from", l => this.rebuild());
    mdds.onIndex("to", l => this.rebuild());
  }

  buildQuery(usp: URLSearchParams): void {
    this.manualUnpairs.forEach(p => usp.append("u", p.left.index + "-" + p.right.index));
    this.manualPairs.forEach(p => usp.append("p", p.left.index + "-" + p.right.index));
  }

  private rebuild(): void {
    this.automatic.clear();
    this.manualPairs.clear();
    this.manualUnpairs.clear();
    this.leftUnpaired.clear();
    this.rightUnpaired.clear();

    // work out automatic pairs
    let left: Entry[] | null = this.mdds.index("from")?.index?.entries ?? null;
    let right: Entry[] | null = this.mdds.index("to")?.index?.entries ?? null;
    if (left !== null && right != null) {
      left.forEach((e, i) => this.leftUnpaired.add({ index: i, entry: e }));
      right.forEach((e, i) => this.rightUnpaired.add({ index: i, entry: e }));


      this.leftUnpaired.forEach(l => {
        this.rightUnpaired.forEach(r => {

          if (this.matches(l.entry, r.entry)) {
            this.automatic.add({
              left: l,
              right: r
            });
            this.rightUnpaired.delete(r);
            this.leftUnpaired.delete(l);
            return;
          }
        });
      });
    }

    // apply unpairs and pairs from URL
    this.route.snapshot.queryParamMap.getAll("u")
      .forEach(v => {
        let [li, ri] = v.split("-").map(s => Number.parseInt(s));
        let auto = Array.from(this.automatic.values())
          .find(p => p.left.index === li && p.right.index == ri);
        if (auto != undefined) {
          this.quietlyUnpair(auto);
        }
      });
    this.route.snapshot.queryParamMap.getAll("p")
      .forEach(v => {
        let [li, ri] = v.split("-").map(s => Number.parseInt(s));
        let le = Array.from(this.leftUnpaired.values())
          .find(p => p.index === li);
        let re = Array.from(this.rightUnpaired.values())
          .find(p => p.index === ri);
        if (le != undefined && re !== undefined) {
          this.quietlyPair(le, re);
        }
      });

    this.rebuildListeners.forEach(cb => cb());
  }

  onRebuild(cb: () => void): void {
    cb();
    this.rebuildListeners.add(cb);
  }

  matches(left: Entry, right: Entry): boolean {
    let match: boolean = left !== null && right !== null;
    if (match) {
      match = left.description === right.description;
    }
    if (match) {
      let lt: Set<string> = new Set(left.tags);
      let rt: Set<string> = new Set(right.tags);
      removeResultTagsFrom(lt, rt);
      match = setEq(lt, rt);
    }
    return match;
  }

  naturallyPaired(): Pair[] {
    return Array.from(this.automatic.values());
  }

  manuallyPaired(): Pair[] {
    return Array.from(this.manualPairs.values());
  }

  /**
   * @returns Paired index entries
   */
  paired(): Pair[] {
    let pairs: Pair[] = [];

    let le = this.mdds.index("from")?.index?.entries ?? null;
    let re = this.mdds.index("to")?.index?.entries ?? null;
    if (le !== null && re !== null) {
      this.automatic.forEach(p => pairs.push(p));
      this.manualPairs.forEach(p => pairs.push(p));
    }

    pairs.sort((a, b) => this.sortPair(a, b));
    return pairs;
  }

  private quietlyUnpair(pair: Pair): boolean {
    let removed = this.automatic.delete(pair);
    if (removed) {
      this.manualUnpairs.add(pair);
    }
    if (!removed) {
      removed = this.manualPairs.delete(pair);
    }
    if (removed) {
      this.leftUnpaired.add(pair.left);
      this.rightUnpaired.add(pair.right);

      return true;
    }
    return false;
  }

  unpair(pair: Pair): void {
    if (this.quietlyUnpair(pair)) {
      this.unpairListeners.forEach(upl => upl(pair));
    }
  }

  onUnpair(cb: (p: Pair) => void) {
    this.unpairListeners.add(cb);
  }

  private quietlyPair(left: IndexedEntry, right: IndexedEntry): Pair | undefined {
    if (
      this.leftUnpaired.delete(left) &&
      this.rightUnpaired.delete(right)) {
      let pair = { left: left, right: right };
      if (this.matches(left.entry, right.entry)) {
        this.automatic.add(pair);

        let original = Array.from(this.manualUnpairs.values())
          .find(p => p.left.entry.detail === left.entry.detail
            && p.right.entry.detail === right.entry.detail);
        if (original !== undefined) {
          this.manualUnpairs.delete(original);
        }
      }
      else {
        this.manualPairs.add(pair);
      }

      return pair;
    }
    return undefined;
  }

  pair(left: IndexedEntry, right: IndexedEntry): void {
    let p = this.quietlyPair(left, right);
    if (p !== undefined) {
      this.pairListeners.forEach(upl => upl(p!));
    }
  }

  onPair(cb: (p: Pair) => void) {
    this.pairListeners.add(cb);
  }

  /**
   * @returns Unpaired index entries on the left
   */
  unpairedLeftEntries(): IndexedEntry[] {
    let lup: IndexedEntry[] = Array.from(this.leftUnpaired);
    lup.sort((a, b) => this.sortEntry(a.entry, b.entry));
    return lup;
  }

  /**
   * @returns Unpaired index entries on the right
   */
  unpairedRightEntries(): IndexedEntry[] {
    let rup: IndexedEntry[] = Array.from(this.rightUnpaired);
    rup.sort((a, b) => this.sortEntry(a.entry, b.entry));
    return rup;
  }

  private sortPair(
    a: Pair,
    b: Pair): number {
    let d = this.sortEntry(a.left.entry, b.left.entry);
    if (d === 0) {
      d = this.sortEntry(a.right.entry, b.right.entry);
    }
    return d;
  }

  private sortEntry(a: Entry, b: Entry): number {
    let d = 0;
    // tags form most of the identity, so sort on that first, ignoring result tags
    let at = a.tags.filter(t => !isResultTag(t));
    let bt = b.tags.filter(t => !isResultTag(t));
    let idx = 0;
    while (d === 0 && idx < at.length && idx < bt.length) {
      d = at[idx].localeCompare(bt[idx]);
      idx++;
    }
    if (d === 0) {
      d = at.length - bt.length;
    }

    // tags are the same, fall back to the description
    if (d === 0) {
      d = a.description.localeCompare(b.description);
    }
    return d;
  }
}

function setEq(a: Set<string>, b: Set<string>): boolean {
  if (a.size != b.size) {
    return false;
  }
  for (var v of a) {
    if (!b.has(v)) {
      return false;
    }
  }
  return true;
}

export interface Pair {
  left: IndexedEntry,
  right: IndexedEntry
}

export interface IndexedEntry {
  index: number,
  entry: Entry
}