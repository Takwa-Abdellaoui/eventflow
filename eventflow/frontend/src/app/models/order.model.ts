export interface Order {
  id: string;
  customerId: string;
  productId: string;
  quantity: number;
  totalAmount: number;
  status: 'PENDING' | 'INVENTORY_RESERVED' | 'CONFIRMED' | 'FAILED' | 'CANCELLED';
  createdAt: string;
  updatedAt: string;
}

export interface OrderRequest {
  customerId: string;
  productId: string;
  quantity: number;
  totalAmount: number;
}

export interface Product {
  id: string;
  name: string;
  price: number;
  stockQuantity: number;
}