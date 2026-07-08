import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { getDashboardSummary, type DashboardWindowSummary } from '../api/dashboard'
import { formatAmount, formatCount, formatStatus, formatWindow } from '../lib/format'

function DashboardSummaryPage() {
  const { organizationId } = useParams<{ organizationId: string }>()
  const { data, isPending, isError } = useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'summary'],
    queryFn: () => getDashboardSummary(organizationId!),
    enabled: Boolean(organizationId),
  })

  if (isPending) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading summary…</p>
  }

  if (isError) {
    return <p className="text-sm text-red-600 dark:text-red-400">Couldn't load the dashboard summary.</p>
  }

  return (
    <div className="grid gap-6 md:grid-cols-3">
      {data.windows.map((window) => (
        <WindowCard key={window.window} window={window} />
      ))}
    </div>
  )
}

function WindowCard({ window }: { window: DashboardWindowSummary }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
      <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400">{formatWindow(window.window)}</h2>
      <p className="mt-2 text-3xl font-semibold text-gray-900 dark:text-gray-50">{formatAmount(window.totalVolume)}</p>
      <p className="text-sm text-gray-500 dark:text-gray-400">
        {formatCount(window.paymentCount)} payment{window.paymentCount === 1 ? '' : 's'}
      </p>
      {window.byStatus.length === 0 ? (
        <p className="mt-4 text-sm text-gray-400 dark:text-gray-500">No activity yet.</p>
      ) : (
        <ul className="mt-4 space-y-1 border-t border-gray-100 pt-4 dark:border-gray-800">
          {window.byStatus.map((status) => (
            <li key={status.status} className="flex items-center justify-between text-sm">
              <span className="text-gray-600 dark:text-gray-300">{formatStatus(status.status)}</span>
              <span className="text-gray-900 dark:text-gray-100">
                {formatCount(status.count)} · {formatAmount(status.totalAmount)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default DashboardSummaryPage
