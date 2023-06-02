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
   * Controls diff display
   */
  @Input() display: Display = "unified";
  /**
   * Controls the grouping of diff lines. Set 0 or less for
   * blocks of unlimited size
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
    this.blocks = linesToBlocks(lines, this.blockSize);
    this.blocks = collapseBlocks(this.blocks, this.context);
  }

  expand(block: Block) {
    block.collapsed = false;
    // provoke our template to be rerendered
    this.cdRef.detectChanges();
  }
}

export type Display = 'unified' | 'split';

/**
 * Added, removed, unchanged
 */
type DiffType = 'added' | 'removed' | 'unchanged';

// A group of line diffs with the same nature (either all multi-chunk, or all 1 chunk of the same type)
interface Block {
  lines: Line[];
  collapsed: boolean;
}

// A single line of content, consisting of one or more chunks
interface Line {
  leftLineNumber: number | null;
  rightLineNumber: number | null;
  chunks: Chunk[];
}

// A single chunk of content, either added, removed or unchanged
interface Chunk {
  type: DiffType;
  content: string;
}

function diff(left: string, right: string) {
  let dmp = new diff_match_patch();
  let diffs = dmp.diff_main(left, right);
  dmp.diff_cleanupSemantic(diffs);
  return diffs;
}

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

    if (content.length > 0) {
      let chunk = { type: dt, content: content };

      if (line === null) {
        // start a new line
        line = {
          leftLineNumber: lln,
          rightLineNumber: rln,
          chunks: [chunk],
        };
        lines.push(line);
      }
      else {
        line.chunks.push(chunk);
      }
    }

    if (nli != -1) {
      line = null;
      if (dt === 'added' || dt === 'unchanged') { rln++; }
      if (dt === 'removed' || dt === 'unchanged') { lln++; }
    }
  }

  return lines;
}

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
      if (block.lines[0].chunks.length > 1
        && line.chunks.length > 1
        && (blockSize <= 0 || block.lines.length < blockSize)) {
        // match! both lines are changes and our blocksize hasn't been reached yet
        block.lines.push(line);
      }
      else if (block.lines[0].chunks.length === 1
        && line.chunks.length === 1
        && block.lines[0].chunks[0].type === line.chunks[0].type) {
        // match! both lines are atomic and of the same type
        block.lines.push(line);
      }
      else {
        // line types are different, start a new block
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
    if (context >= 0
      && block.lines[0].chunks.length === 1
      && block.lines[0].chunks[0].type === 'unchanged'
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