import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Navigate, Link } from 'react-router-dom'
import { listMyOrganizations } from '../api/auth'
import { useAuth } from '../hooks/useAuth'

function RootPage() {
  const { logout } = useAuth()
  const { data, isPending, isError } = useQuery({
    queryKey: ['organizations', 'mine'],
    queryFn: listMyOrganizations,
  })

  if (isPending) {
    return <CenteredMessage>Loading your organizations…</CenteredMessage>
  }

  if (isError) {
    return <CenteredMessage>Couldn't load your organizations. Please try again.</CenteredMessage>
  }

  if (data.length === 0) {
    return <CenteredMessage>You don't belong to any organization yet.</CenteredMessage>
  }

  if (data.length === 1) {
    return <Navigate to={`/organizations/${data[0].organizationId}/dashboard`} replace />
  }

  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-6 bg-gray-50 px-4 dark:bg-gray-950">
      <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-50">Choose an organization</h1>
      <ul className="w-full max-w-sm space-y-2">
        {data.map((org) => (
          <li key={org.organizationId}>
            <Link
              to={`/organizations/${org.organizationId}/dashboard`}
              className="block rounded-md border border-gray-200 bg-white px-4 py-3 text-sm text-gray-900 hover:border-indigo-400 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-100"
            >
              <span className="font-medium">{org.organizationName}</span>
              <span className="ml-2 text-gray-500 dark:text-gray-400">{org.role}</span>
            </Link>
          </li>
        ))}
      </ul>
      <button onClick={logout} className="text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200">
        Log out
      </button>
    </div>
  )
}

function CenteredMessage({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-svh items-center justify-center bg-gray-50 px-4 text-center text-gray-600 dark:bg-gray-950 dark:text-gray-400">
      {children}
    </div>
  )
}

export default RootPage
