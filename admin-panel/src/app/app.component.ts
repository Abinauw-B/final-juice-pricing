import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DashboardComponent } from './dashboard/dashboard.component';
import { JuiceBatchesComponent } from './juice-batches/juice-batches.component';
import { DynamicPricingComponent } from './pricing/dynamic-pricing.component';
import { PricingSimulatorComponent } from './pricing/pricing-simulator.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    DashboardComponent,
    JuiceBatchesComponent,
    DynamicPricingComponent,
    PricingSimulatorComponent
  ],
  template: `
    <div class="admin-layout">
      <nav class="sidebar">
        <div class="sidebar-title">
          <span>🍹 Juice Admin</span>
        </div>

        <div class="nav-link" [class.active]="activeTab === 'dashboard'" (click)="activeTab = 'dashboard'">
          <span>📊 Dashboard &amp; Analytics</span>
        </div>

        <div class="nav-link" [class.active]="activeTab === 'batches'" (click)="activeTab = 'batches'">
          <span>🛢️ 20L Container Batches</span>
        </div>

        <div class="nav-link" [class.active]="activeTab === 'pricing'" (click)="activeTab = 'pricing'">
          <span>📈 Dynamic Pricing Engine</span>
        </div>

        <div class="nav-link" [class.active]="activeTab === 'simulator'" (click)="activeTab = 'simulator'">
          <span>🎮 Pricing Sandbox Simulator</span>
        </div>

        <div style="margin-top: auto; font-size: 12px; color: var(--text-muted); padding-top: 16px; border-top: 1px solid var(--border-color);">
          Spring Boot Production Backend<br>Admin Control Center v1.0
        </div>
      </nav>

      <main class="main-content">
        <app-dashboard *ngIf="activeTab === 'dashboard'"></app-dashboard>
        <app-juice-batches *ngIf="activeTab === 'batches'"></app-juice-batches>
        <app-dynamic-pricing *ngIf="activeTab === 'pricing'"></app-dynamic-pricing>
        <app-pricing-simulator *ngIf="activeTab === 'simulator'"></app-pricing-simulator>
      </main>
    </div>
  `
})
export class AppComponent {
  activeTab: string = 'dashboard';
}
