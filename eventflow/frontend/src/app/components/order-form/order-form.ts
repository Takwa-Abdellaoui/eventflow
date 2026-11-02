import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { OrderService } from '../../services/order.service';
import { Product } from '../../models/order.model';

@Component({
  selector: 'app-order-form',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './order-form.html',
  styleUrl: './order-form.scss'
})
export class OrderForm implements OnInit {
  @Output() orderCreated = new EventEmitter<void>();
  form!: FormGroup;
  products: Product[] = [];
  loading = false;
  success = false;
  error = '';

  constructor(private fb: FormBuilder, private orderService: OrderService) {}

  ngOnInit() {
    this.form = this.fb.group({
      customerId: ['CUST-001', Validators.required],
      productId: ['', Validators.required],
      quantity: [1, [Validators.required, Validators.min(1)]],
      totalAmount: [{ value: 0, disabled: true }]
    });

    this.orderService.getProducts().subscribe(p => {
      this.products = p;
      if (p.length > 0) this.form.patchValue({ productId: p[0].id });
      this.updateTotal();
    });

    this.form.get('productId')?.valueChanges.subscribe(() => this.updateTotal());
    this.form.get('quantity')?.valueChanges.subscribe(() => this.updateTotal());
  }

  updateTotal() {
    const product = this.products.find(p => p.id === this.form.get('productId')?.value);
    const qty = this.form.get('quantity')?.value || 0;
    if (product) this.form.patchValue({ totalAmount: +(product.price * qty).toFixed(2) }, { emitEvent: false });
  }

  submit() {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';
    const val = this.form.getRawValue();
    this.orderService.createOrder(val).subscribe({
      next: () => {
        this.loading = false;
        this.success = true;
        this.orderCreated.emit();
        setTimeout(() => this.success = false, 3000);
      },
      error: () => {
        this.loading = false;
        this.error = 'Erreur lors de la création';
      }
    });
  }
}