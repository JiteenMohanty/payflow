import { useQuery } from '@tanstack/react-query'
import { NavLink, Outlet, useParams } from 'react-router-dom'
import { getOrganization } from '../api/organizations'
import { useAuth } from '../hooks/useAuth'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-1.5 text-sm font-medium ${
    isActive
      ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300'
      : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800'
  }`

function DashboardLayout() {
  const { organizationId } = useParams<{ organizationId: string }>()
  const { logout } = useAuth()
  const { data, isPending, isError } = useQuery({
    queryKey: ['organizations', organizationId],
    queryFn: () => getOrganization(organizationId!),
    enabled: Boolean(organizationId),
  })

  return (
    <div className="min-h-svh bg-gray-50 dark:bg-gray-950">
      <header className="border-b border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
        <div className="flex items-center justify-between px-6 py-4">
          <div>
            <p className="text-xs uppercase tracking-wide text-gray-400">PayFlow Admin</p>
            <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-50">
              {isPending ? 'Loading…' : isError ? 'Organization' : data.name}
            </h1>
          </div>
          <button onClick={logout} className="text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200">
            Log out
          </button>
        </div>
        <nav className="flex gap-1 px-6 pb-3">
          <NavLink to="" end className={navLinkClass}>
            Summary
          </NavLink>
          <NavLink to="payments" className={navLinkClass}>
            Payments
          </NavLink>
          <NavLink to="webhooks" className={navLinkClass}>
            Webhooks
          </NavLink>
          <NavLink to="metrics" className={navLinkClass}>
            Metrics
          </NavLink>
        </nav>
      </header>
      <main className="px-6 py-8">
        {isError ? <p className="text-sm text-red-600 dark:text-red-400">Couldn't load this organization.</p> : <Outlet />}
      </main>
    </div>
  )
}

export default DashboardLayout
