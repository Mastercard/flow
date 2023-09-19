import { Component, Input, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BasisFetchService } from '../basis-fetch.service';
import { SequenceData } from '../flow-sequence/flow-sequence.component';
import { QueryService } from '../query.service';
import { Action, isAction } from '../seq-action/seq-action.component';
import { TxSelectionService } from '../tx-selection.service';
import {
  DataDisplay,
  DiffType,
  Display,
  empty_flow,
  Flow,
  flowsAsserted,
  flowsAssertionsPassed,
  Interaction,
  isDiffFormat,
  Options,
  residueAssertionsPassed,
  residuesAsserted,
} from '../types';
import { IconEmbedService } from '../icon-embed.service';

/**
 * The root component of the flow detail view
 */
@Component({
  selector: 'app-detail',
  templateUrl: './detail.component.html',
  styleUrls: ['./detail.component.css']
})
export class DetailComponent implements OnInit {
  @Input() flow: Flow = empty_flow;
  msgPassed: boolean = false;
  msgFailed: boolean = false;
  rsdPassed: boolean = false;
  rsdFailed: boolean = false;
  noDeps: boolean = true;
  singleDependency: string = "";
  sequence: SequenceData = { entity: [], exercised: [], item: [] };
  options: Options = new Options();
  tabIndex = 0;


  constructor(
    private txSelect: TxSelectionService,
    private query: QueryService,
    private basisFetch: BasisFetchService,
    private title: Title,
    private icons: IconEmbedService,) {

    this.options.display = Display[query.get("display", "Actual") as keyof typeof Display];
    this.options.dataDisplay = DataDisplay[query.get("facet", "Human") as keyof typeof DataDisplay];
    this.options.diffType = DiffType[query.get("diff", "Asserted") as keyof typeof DiffType];
    const dtf: string = query.get("dtf", "unified");
    if (isDiffFormat(dtf)) {
      this.options.diffFormat = dtf;
    }

    this.tabIndex = Number.parseInt(query.get("tab", "0"));
    icons.register(
      "list", "task_alt", "foundation", "groups",
      "check_circle_outline", "error_outline");
  }

  ngOnInit(): void {
    this.sortTags();

    if (flowsAsserted(this.flow)) {
      this.msgPassed = flowsAssertionsPassed(this.flow);
      this.msgFailed = !this.msgPassed;
    }
    if (residuesAsserted(this.flow)) {
      this.rsdPassed = residueAssertionsPassed(this.flow);
      this.rsdFailed = !this.rsdPassed;
    }

    this.title.setTitle(this.flow.description + " " + this.flow.tags);

    this.noDeps = Object.keys(this.flow.dependencies).length === 0;
    this.singleDependency = Object.keys(this.flow.dependencies).length === 1 ?
      Object.keys(this.flow.dependencies)[0]
      : "";

    this.sequence = toSequence(this.flow);
    this.basisFetch.get(this.flow.basis);

    let i: number = parseInt(this.query.get("msg", "-1"));
    let actions: any[] = this.sequence.item.filter(e => isAction(e));

    if (i === -1) {
      // we haven't been directed to any particular message
      // by the url, so let's try and show the most interesting
      // message: either the first assertion error, the first with
      // actual data
      let best = -1;
      let bestScore = -1;
      for (let idx = 0; idx < actions.length; idx++) {

        const e: Action = actions[idx];

        let score = 0;
        let actual = e.transmission.asserted?.actual;
        let expected = e.transmission.asserted?.expect;
        if (actual !== null) {
          // actual data!
          score += 1;
        }
        if ((actual ?? '') !== (expected ?? '')) {
          // assertion error!
          score += 10;
        }

        if (score > bestScore) {
          bestScore = score;
          best = idx;
        }
      }
      i = best;
    }

    if (isAction(actions[i])) {
      this.txSelect.selected(actions[i]);
    }

  }

  peerQuery(): string {
    return "#?inc=" + this.flow.tags.join("&inc=");
  }

  /**
   * Sorts all the tag arrays in the flow
   */
  sortTags(): void {
    this.flow.tags.sort();
    Object.values(this.flow.dependencies).forEach(d => d.tags.sort());
    this.sortInteractionTags(this.flow.root);
  }
  /**
   * Sorts the tags arrays in an interaction and its children
   * @param ntr 
   */
  sortInteractionTags(ntr: Interaction): void {
    ntr.tags.sort();
    ntr.children.forEach(c => this.sortInteractionTags(c));
  }

  onTabChange() {
    if (this.tabIndex !== 0) {
      this.query.set("tab", this.tabIndex.toString());
    }
    else {
      this.query.delete("tab");
    }
  }
}

export function toSequence(flow: Flow): SequenceData {
  const [names, indices] = extractActors(flow.root, [], new Map());
  const actions = extractTransmissions(flow.root, indices, []);
  return { entity: names, exercised: flow.exercised, item: actions };
}

function extractActors(ntr: Interaction, names: string[], indices: Map<string, number>): [string[], Map<string, number>] {
  if (!indices.has(ntr.requester)) {
    names.push(ntr.requester);
    indices.set(ntr.requester, indices.size);
  }
  if (!indices.has(ntr.responder)) {
    names.push(ntr.responder);
    indices.set(ntr.responder, indices.size);
  }
  ntr.children.forEach(c => extractActors(c, names, indices));
  return [names, indices];
}

function extractTransmissions(ntr: Interaction, indices: Map<string, number>, actions: Action[]): Action[] {
  actions.push({
    index: actions.length,
    from: indices.get(ntr.requester) || 0,
    fromName: ntr.requester,
    to: indices.get(ntr.responder) || 0,
    toName: ntr.responder,
    label: ntr.responder + " request",
    tags: ntr.tags,
    transmission: ntr.request
  });
  ntr.children.forEach(c => extractTransmissions(c, indices, actions));
  actions.push({
    index: actions.length,
    from: indices.get(ntr.responder) || 0,
    fromName: ntr.responder,
    to: indices.get(ntr.requester) || 0,
    toName: ntr.requester,
    label: ntr.responder + " response",
    tags: ntr.tags,
    transmission: ntr.response
  });
  return actions;
}