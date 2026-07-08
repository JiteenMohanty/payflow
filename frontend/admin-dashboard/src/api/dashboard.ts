import { apiClient } from './client'

export type PaymentStatus =
  | 'CREATED'
  | 'AUTHORIZED'
  | 'CAPTURED'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED'
  | 'FAILED'
  | 'EXPIRED'

export interface DashboardStatusCount {
  status: PaymentStatus
  count: number
  totalAmount: number
}

export interface DashboardWindowSummary {
  window: string
  paymentCount: number
  totalVolume: number
  byStatus: DashboardStatusCount[]
}

export interface DashboardSummary {
  windows: DashboardWindowSummary[]
}

export async function getDashboardSummary(organizationId: string): Promise<DashboardSummary> {
  const response = await apiClient.get<DashboardSummary>(`/organizations/${organizationId}/dashboard/summary`)
  return response.data
}
