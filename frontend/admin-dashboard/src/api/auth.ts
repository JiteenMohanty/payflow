import { apiClient } from './client'

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
}

export interface RefreshResponse {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
}

export type OrganizationRole = 'OWNER' | 'ADMIN' | 'ANALYST'

export interface OrganizationMembership {
  organizationId: string
  organizationName: string
  organizationSlug: string
  role: OrganizationRole
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/auth/login', { email, password })
  return response.data
}

export async function refreshAccessToken(refreshToken: string): Promise<RefreshResponse> {
  const response = await apiClient.post<RefreshResponse>('/auth/refresh', { refreshToken })
  return response.data
}

export async function listMyOrganizations(): Promise<OrganizationMembership[]> {
  const response = await apiClient.get<OrganizationMembership[]>('/organizations/mine')
  return response.data
}
