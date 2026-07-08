import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { listWebhookDeliveries, listWebhookEndpoints } from '../api/webhooks'
import { formatDateTime } from '../lib/format'
import StatusBadge from '../components/StatusBadge'

function WebhookDeliveriesPage() {
  const { organizationId, endpointId } = useParams<{ organizationId: string; endpointId: string }>()

  const endpointsQuery = useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'webhook-endpoints'],
    queryFn: () => listWebhookEndpoints(organizationId!),
    enabled: Boolean(organizationId),
  })

  const deliveriesQuery = useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'webhook-endpoints', endpointId, 'deliveries'],
    queryFn: () => listWebhookDeliveries(organizationId!, endpointId!),
    enabled: Boolean(organizationId && endpointId),
  })

  const endpoint = endpointsQuery.data?.find((e) => e.id === endpointId)

  return (
    <div className="space-y-4">
      <Link to=".." relative="path" className="text-sm text-indigo-600 hover:underline dark:text-indigo-400">
        ← Back to webhook endpoints
      </Link>

      <div>
        <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-50">{endpoint?.url ?? 'Webhook endpoint'}</h2>
        {endpoint && <p className="text-sm text-gray-500 dark:text-gray-400">{endpoint.subscribedEvents.join(', ')}</p>}
      </div>

      {deliveriesQuery.isPending && <p className="text-sm text-gray-500 dark:text-gray-400">Loading deliveries…</p>}
      {deliveriesQuery.isError && <p className="text-sm text-red-600 dark:text-red-400">Couldn't load deliveries.</p>}
      {deliveriesQuery.data && deliveriesQuery.data.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-gray-400">No deliveries yet.</p>
      )}

      {deliveriesQuery.data && deliveriesQuery.data.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-800">
            <thead className="bg-gray-50 dark:bg-gray-800/50">
              <tr>
                <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  Event
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  Attempt
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  Status
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  Response code
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  Created
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {deliveriesQuery.data.map((delivery) => (
                <tr key={delivery.id}>
                  <td className="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{delivery.eventType}</td>
                  <td className="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{delivery.attemptNumber}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={delivery.status} />
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{delivery.responseCode ?? '—'}</td>
                  <td className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">{formatDateTime(delivery.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default WebhookDeliveriesPage
