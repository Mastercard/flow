import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import mermaid from "mermaid";
import { ModelDiffDataService } from '../model-diff-data.service';
import { Entry, Flow, Interaction } from '../types';
import { FlowFilterService } from '../flow-filter.service';

/**
 * Loads filtered flow data in the current model and generates a
 * system diagram showing actor interactions
 */
@Component({
  selector: 'app-system-diagram',
  templateUrl: './system-diagram.component.html',
  styleUrls: ['./system-diagram.component.css']
})
export class SystemDiagramComponent implements OnInit {
  private readonly modelLabel = "this";

  loadProgress: number = 0;
  summary: string = "";
  diagram: string = "";

  @ViewChild('myTestDiv') containerElRef: ElementRef | null = null;

  constructor(
    private modelData: ModelDiffDataService,
    private filter: FlowFilterService,
  ) {
    modelData.onFlow(this.modelLabel, (label: string, entry: Entry, flow: Flow) => {
      this.loadProgress = modelData.flowLoadProgress(this.modelLabel);
      this.refresh();
    });
    filter.onUpdate(() => {
      this.refresh();
    });
  }

  ngOnInit(): void {
    // start the model load
    this.modelData.path(this.modelLabel, "");
  }

  private refresh(): void {
    let actors: { [key: string]: number } = {};
    let requests: { [key: string]: { [key: string]: number } } = {};
    this.diagram = "";
    this.modelData.index(this.modelLabel)?.index?.entries
      .filter(e => this.filter.passes(e))
      .map(e => this.modelData.flowFor(this.modelLabel, e))
      .filter(f => f != null)
      .forEach(f => this.diagram = this.extractInteraction(
        f!.root, this.diagram, actors, requests)
      );

    let ic = Object.values(requests)
      .map(r => Object.values(r)
        .reduce((acc, val) => acc + val, 0))
      .reduce((acc, val) => acc + val, 0);
    let ac = Object.keys(actors).length;
    this.summary = "" + ic + " interactions between " + ac + " actors";

    if (this.diagram) {
      this.diagram = ("graph LR" + this.diagram).trim();
    }

    if (this.containerElRef != null) {
      // it looks like mermaid doesn't have great support for refreshing
      // an existing diagram - we have to clear an attribute and replace
      // all the svg elements of the previous diagram with our new diagram
      // markup, then trigger mermaid to render again
      this.containerElRef.nativeElement
        .querySelector("pre")
        .removeAttribute("data-processed");
      this.containerElRef.nativeElement
        .querySelector("pre")
        .innerHTML = this.diagram;
    }
    mermaid.init();
  }

  private extractInteraction(
    ntr: Interaction,
    mermaid: string,
    actors: { [key: string]: number },
    requests: { [key: string]: { [key: string]: number } }): string {

    if (requests[ntr.requester] == undefined) {
      requests[ntr.requester] = {};
    }

    if (requests[ntr.requester][ntr.responder] == undefined) {
      mermaid += "\n  " + ntr.requester + " --> " + ntr.responder;
    }

    requests[ntr.requester][ntr.responder] = 1;
    actors[ntr.requester] = 1;
    actors[ntr.responder] = 1;

    ntr.children.forEach(c => {
      mermaid = this.extractInteraction(c, mermaid, actors, requests);
    });

    return mermaid;
  }
}
