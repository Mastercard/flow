import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TagSummaryComponent } from './tag-summary.component';

describe('TagSummaryComponent', () => {
  let component: TagSummaryComponent;
  let fixture: ComponentFixture<TagSummaryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ TagSummaryComponent ]
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
});
