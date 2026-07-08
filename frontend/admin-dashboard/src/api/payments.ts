import { apiClient } from './client'
import type { PaymentStatus } from './dashboard'

export interface Payment {
  id: string
  merchantId: string
  providerReference: string | null
  amount: number
  currency: string
  description: string | null
  status: PaymentStatus
  capturedAmount: number
  refundedAmount: number
  createdAt: string
  authorizedAt: string | null
  capturedAt: string | null
}

export type PaymentActor = 'API' | 'PROVIDER_WEBHOOK' | 'SCHEDULED_JOB'

export interface PaymentTransition {
  fromStatus: PaymentStatus | null
  toStatus: PaymentStatus
  actor: PaymentActor
  reason: string | null
  createdAt: string
}

export interface PaymentDetail {
  payment: Payment
  transitions: PaymentTransition[]
}

export type LedgerEntryType = 'DEBIT' | 'CREDIT'

export interface LedgerEntry {
  id: string
  ledgerTransactionId: string
  paymentId: string
  accountCode: string
  entryType: LedgerEntryType
  amount: number
  currency: string
  createdAt: string
}

export type RefundStatus = 'SUCCEEDED' | 'FAILED'

export interface Refund {
  id: string
  paymentId: string
  amount: number
  currency: string
  status: RefundStatus
  reason: string | null
  providerReference: string | null
  createdAt: string
}

export interface ListPaymentsFilters {
  status?: PaymentStatus
  limit?: number
}

export async function listPayments(organizationId: string, filters: ListPaymentsFilters = {}): Promise<Payment[]> {
  const response = await apiClient.get<Payment[]>(`/organizations/${organizationId}/dashboard/payments`, {
    params: filters,
  })
  return response.data
}

export async function getPayment(organizationId: string, paymentId: string): Promise<PaymentDetail> {
  const response = await apiClient.get<PaymentDetail>(`/organizations/${organizationId}/dashboard/payments/${paymentId}`)
  return response.data
}

export async function getPaymentLedger(organizationId: string, paymentId: string): Promise<LedgerEntry[]> {
  const response = await apiClient.get<LedgerEntry[]>(`/organizations/${organizationId}/dashboard/payments/${paymentId}/ledger`)
  return response.data
}

export async function createRefund(
  organizationId: string,
  paymentId: string,
  amount: number | undefined,
  reason: string | undefined,
): Promise<Refund> {
  const response = await apiClient.post<Refund>(`/organizations/${organizationId}/dashboard/payments/${paymentId}/refunds`, {
    amount,
    reason,
  })
  return response.data
}
