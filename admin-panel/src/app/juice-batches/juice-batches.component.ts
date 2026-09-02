import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-juice-batches',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px;">
        <div>
          <h1 style="font-size: 26px; font-weight: 700; margin-bottom: 4px;">20L Juice Container Batches</h1>
          <p style="color: var(--text-muted);">Manage 20,000 ml container batches, status, and volume tracking.</p>
        </div>
        <div style="display: flex; gap: 12px;">
          <button class="btn-primary" style="background: linear-gradient(135deg, #10b981, #047857);" (click)="showNewFlavourModal = true">🍹 + Add New Juice Variety (Future Use)</button>
          <button class="btn-primary" (click)="showNewBatchModal = true">🛢️ + Register New 20L Batch</button>
        </div>
      </div>

      <div class="section-card">
        <table class="table-custom">
          <thead>
            <tr>
              <th>Batch Code</th>
              <th>Product ID</th>
              <th>Initial Vol (ML)</th>
              <th>Remaining Vol (ML)</th>
              <th>Cup Size</th>
              <th>Est. Remaining Cups</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let b of batches">
              <td style="font-weight: 600; font-family: monospace;">{{ b.batchCode }}</td>
              <td>Product #{{ b.productId }}</td>
              <td>{{ b.initialVolumeMl }} ml (20L)</td>
              <td style="font-weight: 700; color: #3b82f6;">{{ b.remainingVolumeMl }} ml</td>
              <td>{{ b.cupSizeMl }} ml</td>
              <td style="font-weight: 700; color: #10b981;">
                {{ Math.floor(b.remainingVolumeMl / b.cupSizeMl) }} cups
              </td>
              <td>
                <span class="status-tag" [ngClass]="b.status === 'ACTIVE' ? 'tag-active' : 'tag-depleted'">
                  {{ b.status }}
                </span>
              </td>
              <td>
                <button class="btn-primary" style="padding: 4px 10px; font-size: 12px; background: linear-gradient(135deg, #3b82f6, #1d4ed8);" (click)="editJuiceBatch(b)">✏️ Edit Juice</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- New Batch Modal -->
      <div *ngIf="showNewBatchModal" style="position: fixed; top:0; left:0; width:100vw; height:100vh; background: rgba(0,0,0,0.7); display: flex; align-items: center; justify-content: center; z-index: 1000;">
        <div style="background: #1e293b; border: 1px solid var(--border-color); border-radius: 16px; width: 400px; padding: 24px;">
          <h3 style="font-size: 18px; font-weight: 700; margin-bottom: 16px;">Register New 20L Container Batch</h3>
          
          <div style="margin-bottom: 16px;">
            <label style="display: block; font-size: 13px; color: var(--text-muted); margin-bottom: 6px;">Select Flavour Product</label>
            <select [(ngModel)]="newBatchProductId" style="width: 100%; padding: 10px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
              <option [value]="1">Mango Juice (Product #1)</option>
              <option [value]="2">Lemon Juice (Product #2)</option>
              <option [value]="3">Mint Cooler (Product #3)</option>
              <option [value]="4">Orange Sunrise (Product #4)</option>
              <option [value]="5">Strawberry Delight (Product #5)</option>
              <option [value]="6">Royal Grape (Product #6)</option>
              <option [value]="7">Lychee Mist (Product #7)</option>
            </select>
          </div>

          <div style="margin-bottom: 20px;">
            <label style="display: block; font-size: 13px; color: var(--text-muted); margin-bottom: 6px;">Container Capacity (ML)</label>
            <input type="number" [(ngModel)]="newBatchCapacityMl" style="width: 100%; padding: 10px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
          </div>

          <div style="display: flex; gap: 12px; justify-content: flex-end;">
            <button (click)="showNewBatchModal = false" style="padding: 10px 16px; background: transparent; border: 1px solid var(--border-color); color: white; border-radius: 8px;">Cancel</button>
            <button (click)="submitNewBatch()" class="btn-primary">Register Batch</button>
          </div>
        </div>
      </div>

      <!-- New Juice Variety Modal -->
      <div *ngIf="showNewFlavourModal" style="position: fixed; top:0; left:0; width:100vw; height:100vh; background: rgba(0,0,0,0.7); display: flex; align-items: center; justify-content: center; z-index: 1000;">
        <div style="background: #1e293b; border: 1px solid var(--border-color); border-radius: 16px; width: 440px; padding: 24px;">
          <h3 style="font-size: 18px; font-weight: 700; margin-bottom: 16px;">🍹 Add New Juice Variety (Future Use)</h3>
          
          <div style="margin-bottom: 12px;">
            <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Juice Name</label>
            <input type="text" [(ngModel)]="newFlavourName" placeholder="e.g. Pineapple Express" style="width: 100%; padding: 10px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
          </div>

          <div style="margin-bottom: 12px;">
            <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Description</label>
            <input type="text" [(ngModel)]="newFlavourDesc" placeholder="e.g. Fresh tropical crushed pineapple nectar" style="width: 100%; padding: 10px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
          </div>

          <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; margin-bottom: 12px;">
            <div>
              <label style="display: block; font-size: 11px; color: var(--text-muted); margin-bottom: 4px;">Base Price (₹)</label>
              <input type="number" [(ngModel)]="newFlavourBasePrice" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
            </div>
            <div>
              <label style="display: block; font-size: 11px; color: var(--text-muted); margin-bottom: 4px;">Floor (₹)</label>
              <input type="number" [(ngModel)]="newFlavourMinPrice" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
            </div>
            <div>
              <label style="display: block; font-size: 11px; color: var(--text-muted); margin-bottom: 4px;">Ceiling (₹)</label>
              <input type="number" [(ngModel)]="newFlavourMaxPrice" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
            </div>
          </div>

          <div style="margin-bottom: 20px;">
            <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Initial Batch Volume (ML)</label>
            <input type="number" [(ngModel)]="newFlavourVolume" style="width: 100%; padding: 10px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
          </div>

          <div style="display: flex; gap: 12px; justify-content: flex-end;">
            <button (click)="showNewFlavourModal = false" style="padding: 10px 16px; background: transparent; border: 1px solid var(--border-color); color: white; border-radius: 8px;">Cancel</button>
            <button (click)="submitNewFlavour()" class="btn-primary" style="background: linear-gradient(135deg, #10b981, #047857);">✨ Save & Deploy Flavour</button>
          </div>
        </div>
      </div>

      <!-- Edit Juice Modal -->
      <div *ngIf="showEditModal" style="position: fixed; top:0; left:0; width:100vw; height:100vh; background: rgba(0,0,0,0.7); display: flex; align-items: center; justify-content: center; z-index: 1000;">
        <div style="background: #1e293b; border: 1px solid var(--border-color); border-radius: 16px; width: 460px; padding: 24px;">
          <h3 style="font-size: 18px; font-weight: 700; margin-bottom: 16px;">✏️ Edit Juice Variety &amp; Batch Details</h3>

          <div style="margin-bottom: 12px;">
            <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Juice Variety / Name</label>
            <input type="text" [(ngModel)]="editJuiceName" style="width: 100%; padding: 10px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
          </div>

          <div style="margin-bottom: 12px;">
            <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Description</label>
            <input type="text" [(ngModel)]="editJuiceDesc" style="width: 100%; padding: 10px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
          </div>

          <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; margin-bottom: 12px;">
            <div>
              <label style="display: block; font-size: 11px; color: var(--text-muted); margin-bottom: 4px;">Base Price (₹)</label>
              <input type="number" [(ngModel)]="editBasePrice" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
            </div>
            <div>
              <label style="display: block; font-size: 11px; color: var(--text-muted); margin-bottom: 4px;">Floor (₹)</label>
              <input type="number" [(ngModel)]="editMinPrice" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
            </div>
            <div>
              <label style="display: block; font-size: 11px; color: var(--text-muted); margin-bottom: 4px;">Ceiling (₹)</label>
              <input type="number" [(ngModel)]="editMaxPrice" style="width: 100%; padding: 8px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
            </div>
          </div>

          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px;">
            <div>
              <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Remaining Vol (ML)</label>
              <input type="number" [(ngModel)]="editRemainingVol" style="width: 100%; padding: 10px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
            </div>
            <div>
              <label style="display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px;">Status</label>
              <select [(ngModel)]="editStatus" style="width: 100%; padding: 10px; background: #0f172a; color: white; border: 1px solid var(--border-color); border-radius: 8px;">
                <option value="ACTIVE">ACTIVE</option>
                <option value="DEPLETED">DEPLETED</option>
                <option value="PAUSED">PAUSED</option>
              </select>
            </div>
          </div>

          <div style="display: flex; gap: 12px; justify-content: flex-end;">
            <button (click)="showEditModal = false" style="padding: 10px 16px; background: transparent; border: 1px solid var(--border-color); color: white; border-radius: 8px;">Cancel</button>
            <button (click)="submitEditJuice()" class="btn-primary" style="background: linear-gradient(135deg, #3b82f6, #1d4ed8);">💾 Save &amp; Sync Changes</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class JuiceBatchesComponent implements OnInit {
  Math = Math;
  batches: any[] = [];
  showNewBatchModal: boolean = false;
  newBatchProductId: number = 1;
  newBatchCapacityMl: number = 20000;

  showNewFlavourModal: boolean = false;
  newFlavourName: string = '';
  newFlavourDesc: string = '';
  newFlavourBasePrice: number = 25;
  newFlavourMinPrice: number = 20;
  newFlavourMaxPrice: number = 30;
  newFlavourVolume: number = 20000;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.fetchBatches();
  }

  fetchBatches() {
    this.http.get<any[]>('http://localhost:8088/api/batches').subscribe({
      next: (data) => {
        this.batches = data;
      },
      error: () => {
        this.batches = [
          { id: 1, productId: 1, batchCode: 'BATCH-MNG-001', containerCapacityMl: 20000, initialVolumeMl: 20000, remainingVolumeMl: 17500, cupSizeMl: 250, status: 'ACTIVE' },
          { id: 2, productId: 2, batchCode: 'BATCH-LMN-001', containerCapacityMl: 20000, initialVolumeMl: 20000, remainingVolumeMl: 16000, cupSizeMl: 250, status: 'ACTIVE' },
          { id: 3, productId: 3, batchCode: 'BATCH-MNT-001', containerCapacityMl: 20000, initialVolumeMl: 20000, remainingVolumeMl: 19000, cupSizeMl: 250, status: 'ACTIVE' }
        ];
      }
    });
  }

  submitNewBatch() {
    const payload = {
      productId: this.newBatchProductId,
      containerCapacityMl: this.newBatchCapacityMl
    };

    this.http.post('http://localhost:8088/api/batches', payload).subscribe({
      next: () => {
        this.showNewBatchModal = false;
        this.fetchBatches();
      },
      error: () => {
        this.batches.unshift({
          id: Date.now(),
          productId: this.newBatchProductId,
          batchCode: 'BATCH-NEW-' + Math.floor(Math.random() * 1000),
          containerCapacityMl: this.newBatchCapacityMl,
          initialVolumeMl: this.newBatchCapacityMl,
          remainingVolumeMl: this.newBatchCapacityMl,
          cupSizeMl: 250,
          status: 'ACTIVE'
        });
        this.showNewBatchModal = false;
      }
    });
  }

  submitNewFlavour() {
    if (!this.newFlavourName.trim()) {
      alert('Please enter a valid Juice Flavour Name!');
      return;
    }

    const newId = Date.now();
    const newProd = {
      id: newId,
      name: this.newFlavourName.trim(),
      flavour: this.newFlavourName.trim().split(' ')[0].toUpperCase(),
      description: this.newFlavourDesc.trim() || 'Fresh handcrafted juice cooler',
      defaultCupSizeMl: 250,
      currentCupPrice: this.newFlavourBasePrice,
      minCupPrice: this.newFlavourMinPrice,
      maxCupPrice: this.newFlavourMaxPrice
    };

    const shortCode = this.newFlavourName.substring(0, 3).toUpperCase();
    const batchCode = `BATCH-${shortCode}-${Math.floor(100 + Math.random() * 900)}`;

    const newBatch = {
      id: Date.now(),
      productId: newId,
      batchCode: batchCode,
      containerCapacityMl: this.newFlavourVolume,
      initialVolumeMl: this.newFlavourVolume,
      remainingVolumeMl: this.newFlavourVolume,
      cupSizeMl: 250,
      status: 'ACTIVE'
    };

    this.batches.unshift(newBatch);

    const custom = JSON.parse(localStorage.getItem('pubexchange_custom_products') || '[]');
    custom.push(newProd);
    localStorage.setItem('pubexchange_custom_products', JSON.stringify(custom));

    const customBatches = JSON.parse(localStorage.getItem('pubexchange_custom_batches') || '[]');
    customBatches.push(newBatch);
    localStorage.setItem('pubexchange_custom_batches', JSON.stringify(customBatches));

    try {
      const channel = new BroadcastChannel('pubexchange_market_channel');
      channel.postMessage({ type: 'NEW_FLAVOUR_ADDED', payload: { product: newProd, batch: newBatch }, timestamp: Date.now() });
    } catch (e) {}

    this.showNewFlavourModal = false;
    this.newFlavourName = '';
    this.newFlavourDesc = '';
    alert(`🎉 Successfully registered new juice variety "${newProd.name}" and deployed to live POS & LED Display!`);
  }

  showEditModal: boolean = false;
  editBatchCode: string = '';
  editProductId: number = 0;
  editJuiceName: string = '';
  editJuiceDesc: string = '';
  editBasePrice: number = 25;
  editMinPrice: number = 20;
  editMaxPrice: number = 30;
  editRemainingVol: number = 20000;
  editStatus: string = 'ACTIVE';

  editJuiceBatch(batch: any) {
    this.editBatchCode = batch.batchCode;
    this.editProductId = batch.productId;
    this.editRemainingVol = batch.remainingVolumeMl;
    this.editStatus = batch.status;

    const prod = customProds.find((p: any) => p.id === batch.productId) || {
      name: `Product #${batch.productId}`,
      description: 'Fresh handcrafted juice cooler',
      basePrice: 25,
      minCupPrice: 20,
      maxCupPrice: 30
    };

    this.editJuiceName = prod.name;
    this.editJuiceDesc = prod.description || 'Fresh handcrafted juice cooler';
    this.editBasePrice = prod.basePrice || prod.currentCupPrice || 25;
    this.editMinPrice = prod.minCupPrice || 20;
    this.editMaxPrice = prod.maxCupPrice || 30;

    this.showEditModal = true;
  }

  submitEditJuice() {
    if (!this.editJuiceName.trim()) {
      alert('Juice name cannot be empty!');
      return;
    }

    const b = this.batches.find(item => item.batchCode === this.editBatchCode);
    if (b) {
      b.remainingVolumeMl = this.editRemainingVol;
      b.status = this.editStatus;
    }

    const customProds = JSON.parse(localStorage.getItem('pubexchange_custom_products') || '[]');
    let cp = customProds.find((p: any) => p.id === this.editProductId);
    if (!cp) {
      cp = { id: this.editProductId };
      customProds.push(cp);
    }
    cp.name = this.editJuiceName.trim();
    cp.description = this.editJuiceDesc.trim();
    cp.basePrice = this.editBasePrice;
    cp.currentCupPrice = this.editBasePrice;
    cp.minCupPrice = this.editMinPrice;
    cp.maxCupPrice = this.editMaxPrice;
    localStorage.setItem('pubexchange_custom_products', JSON.stringify(customProds));

    try {
      const channel = new BroadcastChannel('pubexchange_market_channel');
      channel.postMessage({
        type: 'PRODUCT_PRICE_UPDATE',
        payload: {
          flavourName: cp.name,
          startPrice: this.editBasePrice,
          minPrice: this.editMinPrice,
          maxPrice: this.editMaxPrice,
          finalPrice: this.editBasePrice
        }
      });
      channel.postMessage({ type: 'NEW_FLAVOUR_ADDED', payload: { product: cp, batch: b } });
    } catch (e) {}

    this.showEditModal = false;
    alert(`✨ Successfully updated "${cp.name}" details!`);
  }
}
