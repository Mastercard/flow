import { Component, Input, OnChanges, OnInit, SimpleChanges, ChangeDetectorRef } from '@angular/core';
import { DIFF_DELETE, DIFF_INSERT, Diff, diff_match_patch } from 'diff-match-patch';

@Component({
  selector: 'app-text-diff',
  templateUrl: './text-diff.component.html',
  styleUrls: ['./text-diff.component.css']
})
export class TextDiffComponent implements OnInit, OnChanges {

  /**
   * One half of the diff - the "before"
   */
  @Input() left: string = "";
  /**
   * The other half of the diff - the "after"
   */
  @Input() right: string = "";
  /**
   * Controls display type - line-by-line or side-by-side
   */
  @Input() display: Display = "unified";
  /**
   * Controls the grouping of diff lines in line-by-line display mode.
   * Set 0 or less for blocks of unlimited size
   */
  @Input() blockSize: number = 0;
  /**
   * Controls the number of context lines to display around
   * diffs. Set a negative number to display all lines
   */
  @Input() context: number = 3;

  blocks: Block[] = [];

  constructor(
    private cdRef: ChangeDetectorRef
  ) {
  }

  ngOnInit(): void {
    this.ngOnChanges();
  }

  ngOnChanges(changes?: SimpleChanges): void {
    let diffs = diff(this.left, this.right);
    let lines = diffToLines(diffs);
    // TODO: don't bother recomputing lines if it's just the blocksize that has changed
    let rawBlocks = linesToBlocks(lines, this.blockSize);
    // TODO: don't bother recomputing blocks if it's just the context that has changed
    this.blocks = collapseBlocks(rawBlocks, this.context);
  }

  expand(block: Block) {
    block.collapsed = false;
    // provoke our template to be rerendered
    this.cdRef.detectChanges();
  }
}

// line-by-line or side-by-side diff display
export type Display = 'unified' | 'split';

// Defines the diff type for a single chunk of text. These values are also used as css class names
type DiffType = 'added' | 'removed' | 'unchanged';

// A single chunk of content, either added, removed or unchanged
interface Chunk {
  type: DiffType;
  content: string;
}

// Defines the diff type for a single line of text (that can contain multiple chunks).
// 'changed' indictes that the line contains chunks with distinct diff types
type LineType = DiffType | 'changed';

// A single line of content, consisting of one or more chunks
interface Line {
  leftLineNumber: number | null;
  rightLineNumber: number | null;
  type: LineType; // this is aguably redundant, but it makes the template much more succinct
  chunks: Chunk[];
}

// A group of line diffs with the same LineType
interface Block {
  lines: Line[];
  collapsed: boolean;
}

// Diffs two strings
function diff(left: string, right: string) {
  let dmp = new diff_match_patch();
  let diffs = dmp.diff_main(left, right);
  dmp.diff_cleanupSemantic(diffs);
  return diffs;
}

// Splits a list of diffs onto lines
function diffToLines(diffs: Diff[]): Line[] {
  let lines: Line[] = [];
  let lln = 1;
  let rln = 1;

  let line: Line | null = null;

  while (diffs.length !== 0) {
    let diff = diffs.shift();
    let dt: DiffType = mapDiffType(diff![0]);
    let content = diff![1];
    const nli = content.indexOf("\n");

    if (nli != -1) {
      // this diff spans a line break, so split it up
      let nextLine = content.substring(nli + 1);
      // note we're not including the \n in our content
      content = content.substring(0, nli);

      if (nextLine.length > 0) {
        // deal with the remnants in the next iteration
        diffs.unshift([diff![0], nextLine]);
      }
    }

    if (line === null) {
      // start a new line
      line = {
        leftLineNumber: lln,
        rightLineNumber: rln,
        type: dt,
        chunks: [],
      };
      lines.push(line);
    }

    if (line.chunks.length === 0 || content.length > 0) {
      line.chunks.push({ type: dt, content: content });
    }

    if (content.length > 0 && line.type !== dt) {
      line.type = 'changed';
    }

    if (nli != -1) {
      line = null;
      if (dt === 'added' || dt === 'unchanged') { rln++; }
      if (dt === 'removed' || dt === 'unchanged') { lln++; }
    }
  }

  return lines;
}

// Converts from the diff type flag used in dff-match-patch to the one used here
function mapDiffType(dmpt: number): DiffType {
  switch (dmpt) {
    case DIFF_DELETE:
      return 'removed';
    case DIFF_INSERT:
      return 'added';
  }
  return 'unchanged';
}

// groups line diffs into similar blocks
function linesToBlocks(lines: Line[], blockSize: number): Block[] {
  let blocks: Block[] = [];

  let block: Block | null = null;

  lines.forEach(line => {
    if (block === null) {
      block = { lines: [line], collapsed: false };
      blocks.push(block);
    }
    else {
      if (block.lines[0].type === 'changed'
        && line.type === 'changed'
        && (blockSize <= 0 || block.lines.length < blockSize)) {
        // match! both lines are changes and our blocksize hasn't been reached yet
        block.lines.push(line);
      }
      else if (block.lines[0].chunks.length === 1
        && line.chunks.length === 1
        && block.lines[0].type === line.type) {
        // match! both lines are atomic and of the same type
        block.lines.push(line);
      }
      else {
        // line types are different (or blockSize has been reached), start a new block
        block = { lines: [line], collapsed: false };
        blocks.push(block);
      }
    }
  });

  return blocks;
}

// Finds long-enough blocks of unchanged lines and splits
// them into 3, with the middle one collapsed
function collapseBlocks(raw: Block[], context: number): Block[] {
  let collapsed: Block[] = [];
  raw.forEach(block => {
    // if we're configured to collapse, and the block is:
    // * comprised of atomic lines
    // * that are unchanged
    if (context >= 0 && block.lines[0].type === 'unchanged'
    ) {
      if (context === 0) {
        // no context, so no need to split, just collapse it
        collapsed.push({ lines: block.lines, collapsed: true });
      }
      else if (block.lines.length > context * 2 + 1) {
        // split into 3
        collapsed.push({ lines: block.lines.slice(0, context), collapsed: false });
        collapsed.push({ lines: block.lines.slice(context, -context), collapsed: true });
        collapsed.push({ lines: block.lines.slice(-context), collapsed: false });
      }
      else {
        // the context encompasses all of the block, no point collapsing
        collapsed.push(block);
      }
    }
    else {
      // either we're not configured to collapse, or the block is unsuitable for it
      collapsed.push(block);
    }
  });
  return collapsed;
}