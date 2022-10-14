import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CollatedChange, DiffPair, FlowDiffService } from '../flow-diff.service';
import { FlowFilterService } from '../flow-filter.service';
import { FlowPairingService } from '../flow-pairing.service';
import { ModelDiffDataService } from '../model-diff-data.service';
import { Entry, removeResultTagsFrom } from '../types';

@Component({
  selector: 'app-change-analysis',
  templateUrl: './change-analysis.component.html',
  styleUrls: ['./change-analysis.component.css']
})
export class ChangeAnalysisComponent implements OnInit {

  changes: CollatedChange[] = [];

  addedEntries: Entry[] = [];
  addedTags: string[] = [];
  freshTags: string[] = [];

  removedEntries: Entry[] = [];
  removedTags: string[] = [];
  deadTags: string[] = [];

  changedFlowCount: number = 0;
  changedTags: string[] = [];
  alwaysTouchedTags: string[] = [];

  unchangedEntries: Entry[] = [];
  unchangedTags: string[] = [];
  untouchedTags: string[] = [];

  viewChangeCall: ((from: string, to: string) => void) = (from: string, to: string) => { };

  constructor(
    private fds: FlowDiffService,
    fps: FlowPairingService,
    private mdds: ModelDiffDataService,
    private filters: FlowFilterService,
    private route: ActivatedRoute,
  ) {
    fds.onFlowData(() => this.rebuild());
    fps.onRebuild(() => this.rebuild());
    fps.onPair(() => this.rebuild());
    fps.onUnpair(() => this.rebuild());
    filters.onUpdate(() => this.rebuild());
  }

  ngOnInit(): void {
  }

  toChangeView(t: (from: string, to: string) => void): void {
    this.viewChangeCall = t;
  }

  private rebuild(): void {
    let filtered = this.fds.sourceData
      .filter(p => this.passesFilters(p));

    let leftTags: Set<string> = new Set();
    let rightTags: Set<string> = new Set();

    let addedTagSet: Set<string> = new Set();
    this.addedEntries = [];
    let removedTagSet: Set<string> = new Set();
    this.removedEntries = [];
    this.changedFlowCount = 0;
    this.unchangedEntries = [];


    let changedTagSet: Set<string> = new Set();
    let unchangedTagSet: Set<string> = new Set();

    filtered.forEach(p => {
      if (p.left != null) {
        if (p.right == null) {
          p.left.entry.tags.forEach(t => removedTagSet.add(t));
          this.removedEntries.push(p.left.entry);
        }
        p.left.entry.tags.forEach(t => leftTags.add(t));
      }
      if (p.right != null) {
        if (p.left == null) {
          p.right.entry.tags.forEach(t => addedTagSet.add(t));
          this.addedEntries.push(p.right.entry);
        }
        p.right.entry.tags.forEach(t => rightTags.add(t));
      }

      if (p.left != null && p.right != null) {
        if (p.left.flat !== p.right.flat) {
          p.left.entry.tags.forEach(t => changedTagSet.add(t));
          p.right.entry.tags.forEach(t => changedTagSet.add(t));
          this.changedFlowCount++;
        }
        else {
          p.left.entry.tags.forEach(t => unchangedTagSet.add(t));
          p.right.entry.tags.forEach(t => unchangedTagSet.add(t));
          this.unchangedEntries.push(p.right.entry);
        }
      }
    });
    removeResultTagsFrom(
      addedTagSet,
      removedTagSet,
      changedTagSet,
      unchangedTagSet,
      leftTags,
      rightTags
    );

    let intersection = Array.from(leftTags).filter(t => rightTags.has(t));
    intersection.forEach(t => {
      leftTags.delete(t);
      rightTags.delete(t)
    });
    this.deadTags = Array.from(leftTags);
    this.deadTags.sort();
    this.freshTags = Array.from(rightTags);
    this.freshTags.sort();

    this.removedTags = Array.from(removedTagSet);
    this.removedTags.sort();

    this.addedTags = Array.from(addedTagSet);
    this.addedTags.sort();

    this.changedTags = Array.from(changedTagSet);
    this.changedTags.sort();

    this.unchangedTags = Array.from(unchangedTagSet);
    this.unchangedTags.sort();

    this.unchangedTags.forEach(t => changedTagSet.delete(t));
    this.alwaysTouchedTags = Array.from(changedTagSet);
    this.alwaysTouchedTags.sort();

    this.changedTags.forEach(t => unchangedTagSet.delete(t));
    this.untouchedTags = Array.from(unchangedTagSet);
    this.untouchedTags.sort();

    this.changes = this.fds.collated
      .filter(ch =>
        ch.pairs.find(p => this.passesFilters(p)) !== undefined)
      .sort((a, b) => {
        // diffs affecting more flows first
        let d = b.pairs.length - a.pairs.length;
        // failing that, larger diffs
        if (d === 0) {
          d = b.diffs.reduce((sum, diff) => sum + diff[1].length, 0)
            - a.diffs.reduce((sum, diff) => sum + diff[1].length, 0);
        }
        // failing that, alpha on flow description
        let idx = 0;
        while (d === 0 && idx < a.pairs.length && idx < b.pairs.length) {
          if (d === 0) {
            let as: string = a.pairs[idx].left?.entry.description ?? "";
            let bs: string = b.pairs[idx].left?.entry.description ?? "";
            d = as.localeCompare(bs);
          }
          if (d === 0) {
            let as: string = a.pairs[idx].right?.entry.description ?? "";
            let bs: string = b.pairs[idx].right?.entry.description ?? "";
            d = as.localeCompare(bs);
          }
          idx++;
        }
        return d;
      });
  }

  private passesFilters(diffpair: DiffPair): boolean {
    let included = false;
    if (diffpair.left != null) {
      included ||= this.filters.passes(diffpair.left.entry);
    }
    if (diffpair.right != null) {
      included ||= this.filters.passes(diffpair.right.entry);
    }
    return included;
  }

  changeTitle(change: CollatedChange): string {
    let diffChars = 0;
    let flowCount = 0;

    change.diffs.forEach(d => diffChars += d[1].length);
    flowCount = change.pairs.filter(p => this.passesFilters(p)).length;

    let ds = diffChars === 1 ? "" : "s";
    let fs = flowCount === 1 ? "" : "s";

    return diffChars + " character" + ds + " on " + flowCount + " flow" + fs;
  }

  tags(change: CollatedChange): string[] {
    let tagSet: Set<string> = new Set();
    change.pairs.forEach(p => {
      if (p.left !== null) {
        p.left.entry.tags.forEach(t => tagSet.add(t));
      }
      if (p.right !== null) {
        p.right.entry.tags.forEach(t => tagSet.add(t));
      }
    });
    removeResultTagsFrom(tagSet);
    let tags = Array.from(tagSet);
    tags.sort();
    return tags;
  }


  leftBasePath(): string {
    return this.mdds.index("from")?.path ?? "";
  }

  leftEntries(change: CollatedChange): Entry[] {
    let entryies: Entry[] = [];
    change.pairs
      .filter(p => this.passesFilters(p))
      .forEach(p => {
        if (p.left !== null) {
          entryies.push(p.left.entry);
        }
      });
    return entryies;
  }

  rightEntries(change: CollatedChange): Entry[] {
    let entryies: Entry[] = [];
    change.pairs
      .filter(p => this.passesFilters(p))
      .forEach(p => {
        if (p.right !== null) {
          entryies.push(p.right.entry);
        }
      });
    return entryies;
  }

  rightBasePath(): string {
    return this.mdds.index("to")?.path ?? "";
  }

  viewChange(pair: DiffPair): void {
    this.viewChangeCall(pair.left!.entry.detail, pair.right!.entry.detail);
  }

}
