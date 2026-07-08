import { apiClient } from './client'

export interface Organization {
  id: string
  name: string
  slug: string
  status: 'ACTIVE' | 'SUSPENDED'
}

export async function getOrganization(organizationId: string): Promise<Organization> {
  const response = await apiClient.get<Organization>(`/organizations/${organizationId}`)
  return response.data
}
