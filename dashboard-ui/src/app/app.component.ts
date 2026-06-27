import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

type Product = {
  productId: string;
  name: string;
  sku: string;
  category: string;
  taxCategory: 'GRAVADO' | 'REDUCIDO' | 'EXENTO';
  unitOfMeasure: string;
};

type Client = {
  clientId: string;
  name: string;
  segment: 'MAYORISTA' | 'MINORISTA';
  taxRegime: 'RESPONSABLE_IVA' | 'NO_RESPONSABLE';
  region: string;
  channel: string;
};

type DraftItem = {
  productId: string;
  quantity: number | null;
  unitPrice: number | null;
};

type ValidDraftItem = DraftItem & {
  quantity: number;
  unitPrice: number;
};

type PublishResponse = {
  orderId: string;
  topic: string;
  status: string;
};

const taxRateByCategory: Record<Product['taxCategory'], number> = {
  GRAVADO: 0.19,
  REDUCIDO: 0.05,
  EXENTO: 0
};

const minUnitPrice = 1;
const maxUnitPrice = 9999;
const maxOrderItemsLimit = 10;

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  private readonly http = inject(HttpClient);

  readonly products = signal<Product[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly loading = signal(true);
  readonly publishing = signal(false);
  readonly error = signal<string | null>(null);
  readonly lastResponse = signal<PublishResponse | null>(null);
  readonly successModal = signal<PublishResponse | null>(null);
  readonly errorModal = signal<string | null>(null);

  readonly orderId = signal('');
  readonly selectedClientId = signal('');
  readonly items = signal<DraftItem[]>([this.emptyItem()]);
  readonly maxOrderItems = maxOrderItemsLimit;

  readonly selectedClient = computed(() =>
    this.clients().find((client) => client.clientId === this.selectedClientId())
  );

  readonly summary = computed(() =>
    this.items().reduce(
      (acc, item) => {
        const product = this.productById(item.productId);
        if (!product) {
          return acc;
        }
        if (!item.quantity || !item.unitPrice) {
          return acc;
        }
        const subtotal = item.quantity * item.unitPrice;
        const taxAmount = subtotal * taxRateByCategory[product.taxCategory];
        acc.subtotal += subtotal;
        acc.totalTax += taxAmount;
        acc.grandTotal += subtotal + taxAmount;
        return acc;
      },
      { subtotal: 0, totalTax: 0, grandTotal: 0 }
    )
  );

  readonly canPublish = computed(() =>
    !this.loading() &&
    !this.publishing() &&
    this.orderId().trim().length > 0 &&
    this.selectedClientId().length > 0 &&
    this.items().some((item) => this.isValidItem(item))
  );

  readonly canAddItem = computed(() => this.items().length < this.maxOrderItems);

  readonly payloadPreview = computed(() => {
    const payload = this.buildPayload(false);
    return payload ? JSON.stringify(payload, null, 2) : 'Completa la orden para previsualizar el JSON Kafka que se enviará.';
  });

  readonly mongoDocumentPreview = computed(() => {
    const document = this.buildExpectedMongoDocument();
    return document
      ? JSON.stringify(document, null, 2)
      : 'Completa la orden para previsualizar el documento enriquecido esperado en MongoDB.';
  });

  constructor() {
    this.loadCatalogs();
  }

  addItem(): void {
    if (this.items().length >= this.maxOrderItems) {
      this.showError(`Solo puedes agregar hasta ${this.maxOrderItems} productos por orden`);
      return;
    }

    this.items.update((items) => {
      if (items.length >= this.maxOrderItems) {
        return items;
      }

      return [...items, this.emptyItem()];
    });
  }

  removeItem(index: number): void {
    this.items.update((items) => items.filter((_, currentIndex) => currentIndex !== index));
    if (this.items().length === 0) {
      this.addItem();
    }
  }

  updateItem(index: number, changes: Partial<DraftItem>): void {
    this.items.update((items) =>
      items.map((item, currentIndex) => (currentIndex === index ? { ...item, ...changes } : item))
    );
  }

  updateQuantity(index: number, value: string | number): void {
    if (value === '') {
      this.updateItem(index, { quantity: null });
      return;
    }

    const numericValue = Number(value);
    const quantity = Number.isFinite(numericValue) ? Math.max(1, numericValue) : null;
    this.updateItem(index, { quantity });
  }

  selectProduct(index: number, productId: string): void {
    const product = this.productById(productId);
    this.updateItem(index, {
      productId,
      unitPrice: this.defaultUnitPrice(product)
    });
  }

  updateUnitPrice(index: number, value: string | number): void {
    if (value === '') {
      this.updateItem(index, { unitPrice: null });
      return;
    }

    const numericValue = Number(value);
    const unitPrice = Number.isFinite(numericValue)
      ? Math.min(maxUnitPrice, Math.max(minUnitPrice, numericValue))
      : null;

    this.updateItem(index, { unitPrice });
  }

  regenerateOrderId(): void {
    this.orderId.set(this.nextOrderId());
  }

  productById(productId: string): Product | undefined {
    return this.products().find((product) => product.productId === productId);
  }

  publish(): void {
    this.error.set(null);
    this.lastResponse.set(null);

    const payload = this.buildPayload(true);
    if (!payload) {
      return;
    }

    this.publishing.set(true);

    this.http.post<PublishResponse>('/api/demo/orders', payload).subscribe({
      next: (response) => {
        this.lastResponse.set(response);
        this.resetForm();
        this.successModal.set(response);
      },
      error: (error) => {
        this.showError(error?.error?.message ?? 'No se pudo publicar la orden');
      },
      complete: () => this.publishing.set(false)
    });
  }

  formatMoney(value: number): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      maximumFractionDigits: 2
    }).format(value);
  }

  closeSuccessModal(): void {
    this.successModal.set(null);
  }

  closeErrorModal(): void {
    this.errorModal.set(null);
  }

  private loadCatalogs(): void {
    this.loading.set(true);
    this.error.set(null);

    this.http.get<Product[]>('/api/products').subscribe({
      next: (products) => {
        this.products.set(products);
      },
      error: () => this.showError('No se pudo cargar el catalogo de productos')
    });

    this.http.get<Client[]>('/api/clients').subscribe({
      next: (clients) => {
        this.clients.set(clients);
      },
      error: () => this.showError('No se pudo cargar el catalogo de clientes'),
      complete: () => this.loading.set(false)
    });
  }

  private resetForm(): void {
    this.orderId.set('');
    this.selectedClientId.set('');
    this.items.set([this.emptyItem()]);
  }

  private buildPayload(reportErrors: boolean): unknown | null {
    const validItems = this.items().filter((item) => this.isValidItem(item));
    if (!this.orderId().trim()) {
      if (reportErrors) {
        this.showError('El Order ID es obligatorio. Puedes escribirlo o generarlo con el boton Regenerar.');
      }
      return null;
    }
    if (!this.selectedClientId()) {
      if (reportErrors) {
        this.showError('Selecciona un cliente');
      }
      return null;
    }
    if (validItems.length === 0) {
      if (reportErrors) {
        this.showError('Agrega al menos un producto valido con cantidad mayor a 0 y precio entre 1 y 9999');
      }
      return null;
    }

    return {
      orderId: this.orderId().trim(),
      clientId: this.selectedClientId(),
      channel: 'B2B',
      createdAt: new Date().toISOString(),
      items: validItems.map((item) => ({
        productId: item.productId,
        quantity: Number(item.quantity),
        unitPrice: Number(item.unitPrice)
      }))
    };
  }

  private buildExpectedMongoDocument(): unknown | null {
    const client = this.selectedClient();
    const validItems = this.items().filter((item) => this.isValidItem(item));
    if (!this.orderId().trim() || !client || validItems.length === 0) {
      return null;
    }

    const enrichedItems = validItems
      .map((item) => {
        const product = this.productById(item.productId);
        if (!product) {
          return null;
        }
        const subtotal = item.quantity * item.unitPrice;
        const taxRate = taxRateByCategory[product.taxCategory];
        const taxAmount = subtotal * taxRate;
        const lineTotal = subtotal + taxAmount;
        return {
          productId: product.productId,
          name: product.name,
          sku: product.sku,
          taxCategory: product.taxCategory,
          quantity: Number(item.quantity),
          unitPrice: this.formatDecimal(item.unitPrice),
          subtotal: this.formatDecimal(subtotal),
          taxRate: taxRate.toFixed(2),
          taxAmount: this.formatDecimal(taxAmount),
          lineTotal: this.formatDecimal(lineTotal)
        };
      })
      .filter((item): item is NonNullable<typeof item> => item !== null);

    const summary = enrichedItems.reduce(
      (acc, item) => {
        acc.subtotal += Number(item.subtotal);
        acc.totalTax += Number(item.taxAmount);
        acc.grandTotal += Number(item.lineTotal);
        return acc;
      },
      { subtotal: 0, totalTax: 0, grandTotal: 0 }
    );

    return {
      _id: 'ObjectId generado por MongoDB',
      orderId: this.orderId().trim(),
      status: 'PROCESSED',
      client: {
        clientId: client.clientId,
        name: client.name,
        segment: client.segment,
        taxRegime: client.taxRegime,
        region: client.region,
        channel: client.channel
      },
      items: enrichedItems,
      summary: {
        subtotal: this.formatDecimal(summary.subtotal),
        totalTax: this.formatDecimal(summary.totalTax),
        grandTotal: this.formatDecimal(summary.grandTotal),
        currency: 'COP'
      },
      processedAt: 'ISODate generado por el worker',
      _class: 'com.b2b.orders.adapters.out.mongodb.document.EnrichedOrderDocument'
    };
  }

  private nextOrderId(): string {
    const timestamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14);
    const randomSuffix = Math.random().toString(36).slice(2, 8).toUpperCase();
    return `ORD-DASH-${timestamp}-${randomSuffix}`;
  }

  private defaultUnitPrice(product: Product | undefined): number {
    if (!product) {
      return minUnitPrice;
    }
    const priceByProduct: Record<string, number> = {
      'PRD-001': 3500,
      'PRD-002': 1800,
      'PRD-003': 5200,
      'PRD-004': 4100,
      'PRD-005': 6800,
      'PRD-006': 9400,
      'PRD-007': 9999,
      'PRD-008': 8200,
      'PRD-009': 3600,
      'PRD-010': 9999
    };
    return priceByProduct[product.productId] ?? 1000;
  }

  private formatDecimal(value: number): string {
    return value.toFixed(2);
  }

  private isValidItem(item: DraftItem): item is ValidDraftItem {
    return Boolean(item.productId) &&
      item.quantity !== null &&
      item.quantity > 0 &&
      item.unitPrice !== null &&
      item.unitPrice >= minUnitPrice &&
      item.unitPrice <= maxUnitPrice;
  }

  private emptyItem(): DraftItem {
    return { productId: '', quantity: null, unitPrice: null };
  }

  private showError(message: string): void {
    this.error.set(message);
    this.errorModal.set(message);
  }
}
