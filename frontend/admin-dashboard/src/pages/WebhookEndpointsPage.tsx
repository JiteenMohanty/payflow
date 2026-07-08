import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { listWebhookEndpoints } from '../api/webhooks'
import { formatDateTime } from '../lib/format'
import StatusBadge from '../components/StatusBadge'

function WebhookEndpointsPage() {
  const { organizationId } = useParams<{ organizationId: string }>()
  const { data, isPending, isError } = useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'webhook-endpoints'],
    queryFn: () => listWebhookEndpoints(organizationId!),
    enabled: Boolean(organizationId),
  })

  return (
    <div>
      <h2 className="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-50">Webhook endpoints</h2>

      {isPending && <p className="text-sm text-gray-500 dark:text-gray-400">Loading webhook endpoints…</p>}
      {isError && <p className="text-sm text-red-600 dark:text-red-400">Couldn't load webhook endpoints.</p>}
      {data && data.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-gray-400">No webhook endpoints registered yet.</p>
      )}

      {data && data.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-800">
            <thead className="bg-gray-50 dark:bg-gray-800/50">
              <tr>
                <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  URL
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  Subscribed events
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  Status
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  Created
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {data.map((endpoint) => (
                <tr key={endpoint.id} className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                  <td className="px-4 py-3 text-sm">
                    <Link to={endpoint.id} className="font-medium text-indigo-600 hover:underline dark:text-indigo-400">
                      {endpoint.url}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600 dark:text-gray-300">{endpoint.subscribedEvents.join(', ')}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={endpoint.status} />
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">{formatDateTime(endpoint.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default WebhookEndpointsPage
