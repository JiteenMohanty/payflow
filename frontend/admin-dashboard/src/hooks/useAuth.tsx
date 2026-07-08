import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { login as loginRequest } from '../api/auth'
import { loadStoredSession, setSession, type AuthSession } from '../api/client'

interface AuthContextValue {
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<AuthSession | null>(loadStoredSession())

  const value = useMemo<AuthContextValue>(
    () => ({
      isAuthenticated: session !== null,
      login: async (email, password) => {
        const response = await loginRequest(email, password)
        const nextSession: AuthSession = { accessToken: response.accessToken, refreshToken: response.refreshToken }
        setSession(nextSession)
        setSessionState(nextSession)
      },
      logout: () => {
        setSession(null)
        setSessionState(null)
      },
    }),
    [session],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
