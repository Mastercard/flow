import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';

import { TagSummaryComponent } from './tag-summary.component';
import { FlowFilterService } from '../flow-filter.service';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { MatTooltipModule } from '@angular/material/tooltip';

describe('TagSummaryComponent', () => {
  let component: TagSummaryComponent;
  let fixture: ComponentFixture<TagSummaryComponent>;
  let mockFilters: jasmine.SpyObj<FlowFilterService>;

  beforeEach(async () => {
    mockFilters = jasmine.createSpyObj(['all', 'addInclude']);
    mockFilters.all.and.returnValue(new Set());
    await TestBed.configureTestingModule({
      declarations: [TagSummaryComponent, StubTag],
      providers: [
        { provide: FlowFilterService, useValue: mockFilters },
      ],
      imports: [
        MatButtonToggleModule,
        MatIconModule,
        MatExpansionModule,
        MatTooltipModule,
        BrowserAnimationsModule,
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TagSummaryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('adds a clicked result tag to the Include filter', () => {
    fixture.componentRef.setInput('entries', [
      { description: 'Passing Flow', tags: ['PASS'], detail: 'pass' },
      { description: 'Failing Flow', tags: ['FAIL'], detail: 'fail' },
    ]);
    fixture.detectChanges();

    const pass = fixture.debugElement.query(By.css('button#PASS'));
    expect(pass.attributes['aria-label']).toBe('Include PASS flows');
    expect(pass.nativeElement.textContent).toContain('50.0%');

    const header = fixture.debugElement.query(By.css('mat-expansion-panel-header'));
    header.query(By.css('mat-panel-title')).nativeElement.click();
    fixture.detectChanges();
    expect(header.attributes['aria-expanded']).toBe('true');

    pass.nativeElement.click();

    expect(mockFilters.addInclude).toHaveBeenCalledOnceWith('PASS');
    expect(header.attributes['aria-expanded']).toBe('true');
  });
});

@Component({
  selector: 'app-tag',
  template: '',
  standalone: false,
})
class StubTag {
  @Input() tag: string = '';
}
