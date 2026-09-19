import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ElementRef } from '@angular/core';
import mermaid from 'mermaid';

import { SystemDiagramComponent } from './system-diagram.component';
import { ModelDiffDataService } from '../model-diff-data.service';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatExpansionModule } from '@angular/material/expansion';

describe('SystemDiagramComponent', () => {
  let component: SystemDiagramComponent;
  let fixture: ComponentFixture<SystemDiagramComponent>;
  let mockMdds;

  beforeEach(async () => {
    mockMdds = jasmine.createSpyObj([
      'path', 'index', 'onFlow', 'flowLoadProgress', 'flowFor']);
    await TestBed.configureTestingModule({
      declarations: [SystemDiagramComponent],
      providers: [
        { provide: ModelDiffDataService, useValue: mockMdds },
      ],
      imports: [
        RouterTestingModule,
        BrowserAnimationsModule,
        MatExpansionModule,
      ]
    })
      .compileComponents();

    fixture = TestBed.createComponent(SystemDiagramComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('normalizes filtered edges after the first asynchronous SVG render', async () => {
    // Keep the real Mermaid renderer: its initial invisible-edge class differs
    // from Flow's canonical style used by subsequent filter/hover updates.
    const container = document.createElement('div');
    container.innerHTML = '<pre class="mermaid"></pre>';
    fixture.nativeElement.appendChild(container);
    component.containerElRef = new ElementRef(container);
    component.edges = [{ from: 'AVA', edge: '~~~', to: 'CHE' }];
    const rendering = spyOn(mermaid, 'init').and.callThrough();
    component.forceRerender();
    // Mermaid's async queue need not belong to the fixture's Angular zone.
    // Observe its actual completion; do not substitute a fake SVG or timer.
    await rendering.calls.mostRecent().returnValue;
    await fixture.whenStable();

    const edge = container.querySelector('path.flowchart-link');
    expect(edge).not.toBeNull();
    expect(edge?.classList.contains('edge-thickness-normal')).toBeTrue();
    expect(edge?.classList.contains('edge-thickness-thick')).toBeFalse();
    expect(edge?.getAttribute('style')).toBe('stroke-width: 0');
    expect(edge?.hasAttribute('marker-end')).toBeFalse();
  });
});
