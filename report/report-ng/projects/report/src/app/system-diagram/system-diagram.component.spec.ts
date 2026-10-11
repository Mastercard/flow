import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SystemDiagramComponent } from './system-diagram.component';
import { ModelDiffDataService } from '../model-diff-data.service';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatExpansionModule } from '@angular/material/expansion';
import { By } from '@angular/platform-browser';
import { MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { FormsModule } from '@angular/forms';
import { MatExpansionPanel } from '@angular/material/expansion';
import { MatProgressSpinner, MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FlowLoadStatus } from '../model-diff-data.service';

describe('SystemDiagramComponent', () => {
  let component: SystemDiagramComponent;
  let fixture: ComponentFixture<SystemDiagramComponent>;
  let mockMdds: jasmine.SpyObj<ModelDiffDataService>;
  let loadStatus: (label: string, status: FlowLoadStatus) => void;

  beforeEach(async () => {
    mockMdds = jasmine.createSpyObj([
      'path', 'index', 'onFlow', 'onFlowLoadStatus', 'flowFor']);
    mockMdds.onFlowLoadStatus.and.callFake((label, callback) => loadStatus = callback);
    await TestBed.configureTestingModule({
      declarations: [SystemDiagramComponent],
      providers: [
        { provide: ModelDiffDataService, useValue: mockMdds },
      ],
      imports: [
        RouterTestingModule,
        BrowserAnimationsModule,
        MatExpansionModule,
        MatButtonToggleModule,
        MatIconModule,
        MatProgressBarModule,
        MatProgressSpinnerModule,
        MatTooltipModule,
        ClipboardModule,
        FormsModule,
      ]
    })
      .compileComponents();

    fixture = TestBed.createComponent(SystemDiagramComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('loads flow details only after expansion', () => {
    fixture.detectChanges();

    expect(mockMdds.path).not.toHaveBeenCalled();

    const panel = fixture.debugElement.query(By.directive(MatExpansionPanel))
      .injector.get(MatExpansionPanel);
    panel.open();
    fixture.detectChanges();

    expect(mockMdds.path).toHaveBeenCalledOnceWith('this', '', 16);

    panel.close();
    panel.open();
    fixture.detectChanges();
    expect(mockMdds.path).toHaveBeenCalledTimes(1);
  });

  it('shows only an accessible spinner while loading', () => {
    fixture.detectChanges();
    fixture.debugElement.query(By.directive(MatExpansionPanel))
      .injector.get(MatExpansionPanel).open();
    loadStatus('this', status(2, 1, 0));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('[aria-label="Loading System diagram"]'))).not.toBeNull();
    const loading = fixture.debugElement.query(By.css('.interaction-loading'));
    expect(getComputedStyle(loading.nativeElement).alignItems).toBe('center');
    expect(loading.query(By.directive(MatProgressSpinner)).componentInstance.diameter).toBe(48);
    expect(fixture.debugElement.query(By.css('#interaction_controls_diagram'))).toBeNull();
    expect(fixture.debugElement.query(By.css('#interaction_summary'))).toBeNull();
  });

  it('renders the completed empty diagram without an indefinite spinner', () => {
    mockMdds.index.and.returnValue({ path: '', index: {
      meta: { modelTitle: '', testTitle: '', timestamp: 0 }, entries: []
    }, status: '' });
    fixture.detectChanges();
    fixture.debugElement.query(By.directive(MatExpansionPanel))
      .injector.get(MatExpansionPanel).open();
    loadStatus('this', status(0, 0, 0));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('[aria-label="Loading System diagram"]'))).toBeNull();
    expect(fixture.debugElement.query(By.css('#interaction_controls_diagram'))).not.toBeNull();
    expect(fixture.debugElement.query(By.css('#interaction_summary')).nativeElement.textContent)
      .toContain('0 interactions between 0 actors');
  });

  it('shows one incomplete diagram after mixed success and failure', () => {
    mockMdds.index.and.returnValue({ path: '', index: {
      meta: { modelTitle: '', testTitle: '', timestamp: 0 }, entries: []
    }, status: '' });
    fixture.detectChanges();
    fixture.debugElement.query(By.directive(MatExpansionPanel))
      .injector.get(MatExpansionPanel).open();
    loadStatus('this', status(2, 2, 1));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('[role="status"]')).nativeElement.textContent)
      .toContain('Incomplete diagram — loaded 1 of 2 Flow details.');
    expect(fixture.debugElement.query(By.css('#interaction_controls_diagram'))).not.toBeNull();
  });

  it('shows a concise failure when every Detail request fails', () => {
    fixture.detectChanges();
    fixture.debugElement.query(By.directive(MatExpansionPanel))
      .injector.get(MatExpansionPanel).open();
    loadStatus('this', status(2, 2, 2));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('[role="alert"]')).nativeElement.textContent)
      .toContain('Unable to load 2 Flow details.');
    expect(fixture.debugElement.query(By.css('#interaction_controls_diagram'))).toBeNull();
  });

  it('hides the orientation selection indicator', () => {
    fixture.detectChanges();
    fixture.debugElement.query(By.directive(MatExpansionPanel))
      .injector.get(MatExpansionPanel).open();
    loadStatus('this', status(0, 0, 0));
    fixture.detectChanges();

    const group = fixture.debugElement.query(By.directive(MatButtonToggleGroup));
    expect(group.injector.get(MatButtonToggleGroup).hideSingleSelectionIndicator).toBeTrue();
  });
});

function status(total: number, settled: number, failed: number): FlowLoadStatus {
  return {
    total,
    settled,
    failed,
    complete: settled === total,
    progress: total === 0 ? 100 : 100 * settled / total,
  };
}
