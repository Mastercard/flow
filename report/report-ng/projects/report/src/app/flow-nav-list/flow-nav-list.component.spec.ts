import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowNavListComponent } from './flow-nav-list.component';
import { MatLegacyListModule as MatListModule } from '@angular/material/legacy-list';

describe('FlowNavListComponent', () => {
  let component: FlowNavListComponent;
  let fixture: ComponentFixture<FlowNavListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        FlowNavListComponent,
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
});
