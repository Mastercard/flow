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
        MatButtonToggleModule,
        MatIconModule,
        MatProgressBarModule,
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

  it('hides the orientation selection indicator', () => {
    component.loadProgress = 100;
    fixture.detectChanges();

    const group = fixture.debugElement.query(By.directive(MatButtonToggleGroup));
    expect(group.injector.get(MatButtonToggleGroup).hideSingleSelectionIndicator).toBeTrue();
  });
});
