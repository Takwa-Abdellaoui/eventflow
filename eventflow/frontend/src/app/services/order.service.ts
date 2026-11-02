import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, interval } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { Order, OrderRequest, Product } from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  // URLs relatives : en production Nginx redirige vers les services,
  // en développement (ng serve) c'est src/proxy.conf.json qui s'en charge.
  private orderApi = '/api/v1/orders';
  private inventoryApi = '/api/v1/inventory';

  constructor(private http: HttpClient) {}

  createOrder(request: OrderRequest): Observable<Order> {
    return this.http.post<Order>(this.orderApi, request);
  }

  getAllOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(this.orderApi);
  }

  getProducts(): Observable<Product[]> {
    return this.http.get<Product[]>(`${this.inventoryApi}/products`);
  }

  pollOrders(ms = 2000): Observable<Order[]> {
    return interval(ms).pipe(switchMap(() => this.getAllOrders()));
  }
}
