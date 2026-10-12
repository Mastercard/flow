import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowNavListComponent } from './flow-nav-list.component';
import { MatListModule } from '@angular/material/list';
import { By } from '@angular/platform-browser';
import { CdkDrag, CdkDropList } from '@angular/cdk/drag-drop';
import { Component, Input } from '@angular/core';
import { Entry } from '../types';

describe('FlowNavListComponent', () => {
  let component: FlowNavListComponent;
  let fixture: ComponentFixture<FlowNavListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        FlowNavListComponent,
        StubFlowNavItem,
      ],
      imports: [
        MatListModule,
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowNavListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders ordinary rows without drag and drop directives', () => {
    fixture.componentRef.setInput('entries', [{ description: 'Flow', tags: [], detail: 'flow' }]);
    fixture.detectChanges();

    expect(fixture.debugElement.queryAll(By.directive(CdkDrag))).toEqual([]);
    expect(fixture.debugElement.queryAll(By.directive(CdkDropList))).toEqual([]);
  });

  it('retains an ordinary row with the same Detail identity', () => {
    fixture.componentRef.setInput('entries', [{ description: 'Before', tags: [], detail: 'flow' }]);
    fixture.detectChanges();
    const row = fixture.debugElement.query(By.css('mat-nav-list > div')).nativeElement;

    fixture.componentRef.setInput('entries', [{ description: 'After', tags: [], detail: 'flow' }]);
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('mat-nav-list > div')).nativeElement).toBe(row);
  });
});

@Component({
  selector: 'app-flow-nav-item',
  template: '',
  standalone: false,
})
class StubFlowNavItem {
  @Input() entry: Entry = { description: '', tags: [], detail: '' };
  @Input() basePath: string = '';
  @Input() showResult: boolean = true;
}
