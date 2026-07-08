import { apiClient } from './client'

export type WebhookEndpointStatus = 'ACTIVE' | 'DISABLED'

export interface WebhookEndpoint {
  id: string
  url: string
  subscribedEvents: string[]
  status: WebhookEndpointStatus
  createdAt: string
}

export type WebhookDeliveryStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'EXHAUSTED'

export interface WebhookDelivery {
  id: string
  eventType: string
  attemptNumber: number
  status: WebhookDeliveryStatus
  responseCode: number | null
  nextRetryAt: string | null
  createdAt: string
}

export async function listWebhookEndpoints(organizationId: string): Promise<WebhookEndpoint[]> {
  const response = await apiClient.get<WebhookEndpoint[]>(`/organizations/${organizationId}/dashboard/webhook-endpoints`)
  return response.data
}

export async function listWebhookDeliveries(organizationId: string, endpointId: string): Promise<WebhookDelivery[]> {
  const response = await apiClient.get<WebhookDelivery[]>(
    `/organizations/${organizationId}/dashboard/webhook-endpoints/${endpointId}/deliveries`,
  )
  return response.data
}
