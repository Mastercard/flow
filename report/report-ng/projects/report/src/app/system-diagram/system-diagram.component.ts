import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import mermaid from "mermaid";
import { ModelDiffDataService } from '../model-diff-data.service';
import { Entry, Flow, Interaction } from '../types';
import { FlowFilterService } from '../flow-filter.service';
import { EntryHoverService } from '../entry-hover.service';
import { MatButtonToggleChange } from '@angular/material/button-toggle';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { IconEmbedService } from '../icon-embed.service';

interface Edge {
  from: string;
  edge: string;
  to: string;
}

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
  private readonly invisibleEdge = '~~~';
  private readonly dottedEdge = '-.->';
  private readonly lineEdge = '-->';
  private readonly thickEdge = '==>';

  loadProgress: number = 0;
  summary: string = "";
  edges: Edge[] = [];
  renderedEdgeCount: number = 0;
  hovered: Entry | null = null;
  orientation: string = "LR";
  hideFilteredActors: boolean = false;
  mermaidMarkup: string = "";

  @ViewChild('myTestDiv') containerElRef: ElementRef | null = null;

  constructor(
    private modelData: ModelDiffDataService,
    private filter: FlowFilterService,
    private hover: EntryHoverService,
    icons: IconEmbedService,
  ) {
    modelData.onFlow(this.modelLabel, (label: string, entry: Entry, flow: Flow) => {
      this.loadProgress = modelData.flowLoadProgress(this.modelLabel);
      this.refreshEdges();
    });
    filter.onUpdate(() => {
      if (this.hideFilteredActors) {
        this.refreshEdges();
      }
      else {
        this.refilterEdges();
      }
    });
    hover.onHover((entry: Entry | null) => {
      this.hovered = entry;
      this.rehoverFlow();
    });

    icons.register(
      "panorama_vertical", "panorama_horizontal",
      "filter_alt", "content_copy");
  }

  ngOnInit(): void {
    // we can't do the get requests to load flow data
    // when we're browsing on the file system, so let's
    // avoid errors in the log by not even trying
    if (location.protocol !== "file:") {
      // start the model load
      this.modelData.path(this.modelLabel, "");
    }
  }

  forceRerender(): void {
    this.renderedEdgeCount = -1;
    this.refilterEdges();
  }

  updateFilterHide(event: MatButtonToggleChange) {
    this.hideFilteredActors = event.source.checked;
    this.renderedEdgeCount = -1;
    this.refreshEdges();
  }

  copyContent(event: MatButtonToggleChange): void {
    // we're abusing a toggle button for the visuals, so keep it unchecked
    event.source.checked = false;
  }

  /**
   * Called when a flow has been loaded, recalculates all the edges in the system
   */
  private refreshEdges(): void {

    let requests: { [key: string]: { [key: string]: number } } = {};
    this.edges = [];

    this.modelData.index(this.modelLabel)?.index?.entries
      .filter(e => !this.hideFilteredActors || this.filter.passes(e))
      .map(e => this.modelData.flowFor(this.modelLabel, e))
      .filter(f => f != null)
      .forEach(f => this.extractEdges(
        f!.root, this.edges, requests)
      );

    // force a re-render of the diagram
    this.renderedEdgeCount = -1;
    this.refilterEdges();
  }

  /**
   * Recurses through an interaction structure and extracts the requester/responder edges
   * @param ntr An interaction
   * @param edges An ordered record of unique requester/responder pairs
   * @param requests The requester/responder pairs that we've already seen
   */
  private extractEdges(
    ntr: Interaction,
    edges: Edge[],
    requests: { [key: string]: { [key: string]: number } }): void {

    if (requests[ntr.requester] == undefined) {
      requests[ntr.requester] = {};
    }

    if (requests[ntr.requester][ntr.responder] == undefined) {
      edges.push({ from: ntr.requester, edge: '', to: ntr.responder });
    }

    requests[ntr.requester][ntr.responder] = 1;

    ntr.children.forEach(c => this.extractEdges(c, edges, requests)
    );
  }

  /**
   * Called when edge data or filters have changed, updates our calculated 
   * edges to show the filtered subset
   */
  private refilterEdges(): void {
    let requests: { [key: string]: { [key: string]: number } } = {};
    let filtered: Edge[] = [];

    this.modelData.index(this.modelLabel)?.index?.entries
      .filter(e => this.filter.passes(e))
      .map(e => this.modelData.flowFor(this.modelLabel, e))
      .filter(f => f != null)
      .forEach(f => this.extractEdges(
        f!.root, filtered, requests)
      );

    this.edges
      .forEach(e => e.edge = this.invisibleEdge);
    this.edges
      .filter(e => filtered
        .filter(f => f.from === e.from && f.to === e.to)
        .length != 0)
      .forEach(e => e.edge = this.lineEdge);

    this.rehoverFlow();
  }

  /**
   * Called when edge data or filters of hovered entry has changed, updates
   * our edges to highlight the hovered flow
   */
  private rehoverFlow(): void {
    if (this.hovered !== null) {

      let flow = this.modelData.flowFor(this.modelLabel, this.hovered);
      if (flow !== null) {

        let requests: { [key: string]: { [key: string]: number } } = {};
        let highlighted: Edge[] = [];
        this.extractEdges(flow.root, highlighted, requests);

        this.edges
          .filter(e => e.edge !== this.invisibleEdge)
          .forEach(e => e.edge = this.dottedEdge);
        this.edges
          .filter(e => highlighted
            .filter(f => f.from === e.from && f.to === e.to)
            .length != 0)
          .forEach(e => e.edge = this.thickEdge);
      }
    }
    else {
      this.edges
        .filter(e => e.edge !== this.invisibleEdge)
        .forEach(e => e.edge = this.lineEdge);
    }
    this.rerender();
  }

  /**
   * Called when anything has changed, refreshes our diagram and the summary
   */
  private rerender(): void {
    let ic = this.edges
      .filter(e => e.edge === this.lineEdge || e.edge == this.thickEdge)
      .length;
    let actors: Set<string> = new Set();
    this.edges
      .filter(e => e.edge === this.lineEdge || e.edge == this.thickEdge)
      .forEach(e => actors.add(e.from).add(e.to));
    let ac = actors.size;
    this.summary = ic + " interactions between " + ac + " actors";

    this.mermaidMarkup = "graph " + this.orientation + "\n" + this.edges
      .map(e => "  " + e.from + " " + e.edge + " " + e.to)
      .join("\n");

    if (this.containerElRef != null) {
      if (this.edges.length != this.renderedEdgeCount) {
        // we've got a new edge - we have to rerender the diagram completely

        // it looks like mermaid doesn't have great support for refreshing
        // an existing diagram - we have to clear an attribute and manually
        // delete the generated svg
        this.containerElRef.nativeElement
          .querySelector("pre")
          .removeAttribute("data-processed");
        this.containerElRef.nativeElement
          .querySelector("pre")
          .innerHTML = "";

        if (this.edges.length > 0) {
          // now we know we have something to draw, put that text into
          // the dom and trigger mermaid
          this.containerElRef.nativeElement
            .querySelector("pre")
            .innerHTML = this.mermaidMarkup;

          mermaid.init();
          this.renderedEdgeCount = this.edges.length;
        }
      }
      else {
        // edge count hasn't changed, so we can get away with editing styles

        // style names have the svg's ID (which is dynamic) as a prefix, so we have to find that first
        let markers: HTMLElement[] = (Array.from(this.containerElRef?.nativeElement
          .querySelectorAll("marker")) as HTMLElement[])
          .filter(marker => marker.id.includes("flowchart-pointEnd"));
        let pointEndId = markers.length > 0 ? markers[0].id : "unknown!";

        let paths: HTMLElement[] = (Array.from(this.containerElRef?.nativeElement
          .querySelectorAll("path")) as HTMLElement[])
          .filter(path => path.classList.contains("flowchart-link"));

        this.edges.forEach(edge => {
          paths
            .filter(path => path.classList.contains("LS-" + edge.from)
              && path.classList.contains("LE-" + edge.to))
            .forEach(path => {
              if (edge.edge === this.dottedEdge) {
                this.dottedEdgeStyle(path, pointEndId);
              }
              else if (edge.edge === this.thickEdge) {
                this.thickEdgeStyle(path, pointEndId);
              }
              else if (edge.edge === this.invisibleEdge) {
                this.invisibleEdgeStyle(path);
              }
              else {
                this.lineEdgeStyle(path, pointEndId);
              }
            });
        });
      }
    }
  }

  private lineEdgeStyle(e: HTMLElement, pointEndId: string): void {
    this.replaceClass(e, "edge-thickness-", "normal");
    this.replaceClass(e, "edge-pattern-", "solid");
    this.setAttr(e, "style", "");
    this.setAttr(e, "marker-end", "url(#" + pointEndId + ")");
  }

  private thickEdgeStyle(e: HTMLElement, pointEndId: string): void {
    this.replaceClass(e, "edge-thickness-", "thick");
    this.replaceClass(e, "edge-pattern-", "solid");
    this.setAttr(e, "style", "");
    this.setAttr(e, "marker-end", "url(#" + pointEndId + ")");
  }

  private dottedEdgeStyle(e: HTMLElement, pointEndId: string): void {
    this.replaceClass(e, "edge-thickness-", "normal");
    this.replaceClass(e, "edge-pattern-", "dotted");
    this.setAttr(e, "style", "");
    this.setAttr(e, "marker-end", "url(#" + pointEndId + ")");
  }

  private invisibleEdgeStyle(e: HTMLElement): void {
    this.replaceClass(e, "edge-thickness-", "normal");
    this.replaceClass(e, "edge-pattern-", "solid");
    this.setAttr(e, "style", "stroke-width: 0");
    this.setAttr(e, "marker-end", "");
  }

  private replaceClass(e: HTMLElement, prefix: string, suffix: string) {
    e.classList.forEach(c => {
      if (c.startsWith(prefix)) {
        e.classList.remove(c);
      }
    });
    e.classList.add(prefix + suffix);
  }

  private setAttr(e: HTMLElement, name: string, value: string) {
    if (value) {
      e.setAttribute(name, value);
    }
    else {
      e.removeAttribute(name);
    }
  }
}
