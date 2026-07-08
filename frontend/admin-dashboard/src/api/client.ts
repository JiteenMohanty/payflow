import axios from 'axios'

const STORAGE_KEY = 'payflow.auth.session'

export interface AuthSession {
  accessToken: string
  refreshToken: string
}

export function loadStoredSession(): AuthSession | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthSession
  } catch {
    return null
  }
}

let currentSession: AuthSession | null = loadStoredSession()

export function setSession(session: AuthSession | null): void {
  currentSession = session
  if (session) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } else {
    localStorage.removeItem(STORAGE_KEY)
  }
}

export const apiClient = axios.create({
  baseURL: '/v1',
})

apiClient.interceptors.request.use((config) => {
  if (currentSession && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${currentSession.accessToken}`
  }
  return config
})

let refreshInFlight: Promise<AuthSession> | null = null

async function refreshSession(): Promise<AuthSession> {
  if (!currentSession) {
    throw new Error('No session to refresh')
  }
  const response = await axios.post<{ accessToken: string; refreshToken: string }>(
    '/v1/auth/refresh',
    { refreshToken: currentSession.refreshToken },
  )
  const refreshed: AuthSession = { accessToken: response.data.accessToken, refreshToken: response.data.refreshToken }
  setSession(refreshed)
  return refreshed
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    const isAuthEndpoint = originalRequest?.url?.startsWith('/auth/')
    if (error.response?.status === 401 && currentSession && !originalRequest._retried && !isAuthEndpoint) {
      originalRequest._retried = true
      try {
        refreshInFlight ??= refreshSession().finally(() => {
          refreshInFlight = null
        })
        const refreshed = await refreshInFlight
        originalRequest.headers.Authorization = `Bearer ${refreshed.accessToken}`
        return apiClient(originalRequest)
      } catch {
        setSession(null)
        window.location.assign('/login')
        return Promise.reject(error)
      }
    }
    return Promise.reject(error)
  },
)
