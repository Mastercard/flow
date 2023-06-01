import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ResidueViewComponent } from './residue-view.component';
import { MatExpansionModule } from '@angular/material/expansion';

describe('ResidueViewComponent', () => {
  let component: ResidueViewComponent;
  let fixture: ComponentFixture<ResidueViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        ResidueViewComponent,
      ],
      imports: [
        MatExpansionModule,
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ResidueViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
