import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-pricing-simulator',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <h1 style="font-size: 26px; font-weight: 700; margin-bottom: 4px;">Dynamic Pricing Sandbox Simulator</h1>
      <p style="color: var(--text-muted); margin-bottom: 24px;">Simulate customer purchasing scenarios over time without modifying live production database tables.</p>

      <div style="display: grid; grid-template-columns: 340px 1fr; gap: 24px;">
        <!-- Control Form -->
        <div class="section-card">
          <h2 style="font-size: 18px; font-weight: 600; margin-bottom: 16px;">Simulation Parameters</h2>
          
          <div style="margin-bottom: 14px;">
            <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Juice Flavour</label>
            <input type="text" [(ngModel)]="req.flavourName" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 6px;">
          </div>

          <div style="margin-bottom: 14px;">
            <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Initial Volume (ML)</label>
            <input type="number" [(ngModel)]="req.initialVolumeMl" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 6px;">
            <span style="font-size: 11px; color: var(--text-muted);">20,000 ML = 20 Litres (80 cups)</span>
          </div>

          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 14px;">
            <div>
              <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Start Price (₹)</label>
              <input type="number" [(ngModel)]="req.initialPrice" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 6px;">
            </div>
            <div>
              <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Cups / Step</label>
              <input type="number" [(ngModel)]="req.cupsPerInterval" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 6px;">
            </div>
          </div>

          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 16px;">
            <div>
              <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Min Limit (₹)</label>
              <input type="number" [(ngModel)]="req.minPrice" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 6px;">
            </div>
            <div>
              <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Max Limit (₹)</label>
              <input type="number" [(ngModel)]="req.maxPrice" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 6px;">
            </div>
          </div>

          <button class="btn-primary" style="width: 100%; padding: 12px;" (click)="runSimulation()">▶️ Run Sandbox Simulation</button>
        </div>

        <!-- Simulation Results & Timeline -->
        <div>
          <div *ngIf="simulationResult" class="metric-grid">
            <div class="metric-card">
              <div class="metric-label">Simulated Flavour</div>
              <div class="metric-val" style="font-size: 20px;">{{ simulationResult.flavourName }}</div>
            </div>
            <div class="metric-card">
              <div class="metric-label">Initial vs Final Price</div>
              <div class="metric-val" style="font-size: 20px; color: #10b981;">₹{{ simulationResult.initialPrice }} &rarr; ₹{{ simulationResult.finalPrice }}</div>
            </div>
            <div class="metric-card">
              <div class="metric-label">Total Cups Sold</div>
              <div class="metric-val" style="font-size: 20px; color: #3b82f6;">{{ simulationResult.totalCupsSold }} cups</div>
            </div>
          </div>

          <div *ngIf="simulationResult" class="section-card">
            <h2 style="font-size: 18px; font-weight: 600; margin-bottom: 16px;">Step-by-Step Price Trajectory Timeline</h2>
            <table class="table-custom">
              <thead>
                <tr>
                  <th>Step</th>
                  <th>Time</th>
                  <th>Rem. Vol (ML)</th>
                  <th>Cups Sold</th>
                  <th>W0</th>
                  <th>W1</th>
                  <th>W2</th>
                  <th>Weighted Sales</th>
                  <th>Target Sales</th>
                  <th>Demand Ratio</th>
                  <th>Movement</th>
                  <th>Current Price</th>
                  <th>Explanation Log</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let step of simulationResult.steps">
                  <td style="font-weight: 600;">#{{ step.stepIndex }}</td>
                  <td style="font-size: 13px; color: var(--text-muted);">{{ step.timeStr }}</td>
                  <td style="font-size: 13px;">{{ step.remainingVolumeMl }} ml</td>
                  <td style="font-weight: 600;">+{{ step.cupsSoldThisStep }}</td>
                  <td>{{ step.w0 }}</td>
                  <td>{{ step.w1 }}</td>
                  <td>{{ step.w2 }}</td>
                  <td>{{ step.weightedSales?.toFixed(2) }}</td>
                  <td>{{ step.targetSales?.toFixed(2) }}</td>
                  <td>{{ step.demandRatio?.toFixed(2) }}</td>
                  <td>
                    <span class="status-tag" [ngClass]="getMovementClass(step.priceMovement)">
                      {{ step.priceMovement }}
                    </span>
                  </td>
                  <td style="font-weight: 700; font-size: 16px; color: #10b981;">₹{{ step.price?.toFixed(2) }}</td>
                  <td style="font-size: 12px; color: var(--text-muted); max-width: 280px;">{{ step.explanation }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  `
})
export class PricingSimulatorComponent {
  Math = Math;
  req: any = {
    flavourName: 'Fresh Mango Juice',
    initialVolumeMl: 20000,
    initialPrice: 25,
    minPrice: 18,
    maxPrice: 35,
    totalSimulatedPurchases: 40,
    cupsPerInterval: 4,
    intervalMinutes: 1,
    targetSales: 0.55,
    startTimeStr: '12:00'
  };

  simulationResult: any = null;

  constructor(private http: HttpClient) {
    this.runSimulation();
  }

  runSimulation() {
    this.http.post('http://localhost:8088/api/pricing/simulate', this.req).subscribe({
      next: (res: any) => {
        this.simulationResult = res;
      },
      error: () => {
        // Authoritative DWMA fallback
        const steps = [];
        let vol = this.req.initialVolumeMl;
        let price = this.req.initialPrice;
        let totalSold = 0;
        const salesHistory: number[] = [];

        for (let i = 1; i <= 10; i++) {
          const sold = Math.min(this.req.cupsPerInterval, Math.floor(vol / 250));
          vol -= sold * 250;
          totalSold += sold;
          salesHistory.push(sold);

          const w0 = sold;
          const w1 = salesHistory.length >= 2 ? salesHistory[salesHistory.length - 2] : 0;
          const w2 = salesHistory.length >= 3 ? salesHistory[salesHistory.length - 3] : 0;
          const sw = (1.00 * w0) + (0.50 * w1) + (0.25 * w2);
          const target = this.req.targetSales || 0.55;
          const rd = sw / target;

          let movement = '₹0';
          let delta = 0;
          if (rd >= 1.10) {
            delta = (w0 > 0) ? 1 : 0;
            movement = (w0 > 0) ? '+₹1' : '₹0';
          } else if (rd >= 0.90) {
            delta = 0;
            movement = '₹0';
          } else {
            delta = -1;
            movement = '-₹1';
          }
          const oldP = price;
          price = Math.min(this.req.maxPrice, Math.max(this.req.minPrice, price + delta));

          steps.push({
            stepIndex: i,
            timeStr: `12:${((i - 1) * 1).toString().padStart(2, '0')}`,
            remainingVolumeMl: vol,
            estimatedRemainingCups: Math.floor(vol / 250),
            cupsSoldThisStep: sold,
            cumulativeCupsSold: totalSold,
            w0: w0,
            w1: w1,
            w2: w2,
            weightedSales: sw,
            targetSales: target,
            demandRatio: rd,
            price: price,
            priceMovement: movement,
            explanation: `Step ${i}: W0=${w0}, W1=${w1}, W2=${w2} | S_w=${sw.toFixed(2)}, Target=${target.toFixed(2)} cups/min, R_d=${rd.toFixed(2)} => ${movement} (₹${oldP.toFixed(2)} -> ₹${price.toFixed(2)})`
          });
        }

        this.simulationResult = {
          flavourName: this.req.flavourName,
          initialVolumeMl: this.req.initialVolumeMl,
          finalVolumeMl: vol,
          initialPrice: this.req.initialPrice,
          finalPrice: price,
          totalCupsSold: totalSold,
          steps: steps
        };
      }
    });
  }

  getMovementClass(m: string): string {
    if (m === '+₹1') return 'tag-depleted';
    if (m.includes('-')) return 'tag-crash';
    return 'tag-active';
  }
}

