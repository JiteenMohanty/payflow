import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { listPayments } from '../api/payments'
import type { PaymentStatus } from '../api/dashboard'
import { formatAmount, formatDateTime } from '../lib/format'
import StatusBadge from '../components/StatusBadge'

const STATUS_OPTIONS: PaymentStatus[] = [
  'CREATED',
  'AUTHORIZED',
  'CAPTURED',
  'PARTIALLY_REFUNDED',
  'REFUNDED',
  'FAILED',
  'EXPIRED',
]

function PaymentListPage() {
  const { organizationId } = useParams<{ organizationId: string }>()
  const [statusFilter, setStatusFilter] = useState<PaymentStatus | ''>('')

  const { data, isPending, isError } = useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'payments', statusFilter],
    queryFn: () => listPayments(organizationId!, statusFilter ? { status: statusFilter } : {}),
    enabled: Boolean(organizationId),
  })

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-50">Payments</h2>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as PaymentStatus | '')}
          className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-200"
        >
          <option value="">All statuses</option>
          {STATUS_OPTIONS.map((status) => (
            <option key={status} value={status}>
              {status}
            </option>
          ))}
        </select>
      </div>

      {isPending && <p className="text-sm text-gray-500 dark:text-gray-400">Loading payments…</p>}
      {isError && <p className="text-sm text-red-600 dark:text-red-400">Couldn't load payments.</p>}
      {data && data.length === 0 && <p className="text-sm text-gray-500 dark:text-gray-400">No payments found.</p>}

      {data && data.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-800">
            <thead className="bg-gray-50 dark:bg-gray-800/50">
              <tr>
                <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  Description
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  Amount
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
              {data.map((payment) => (
                <tr key={payment.id} className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                  <td className="px-4 py-3 text-sm">
                    <Link to={payment.id} className="font-medium text-indigo-600 hover:underline dark:text-indigo-400">
                      {payment.description ?? payment.id}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
                    {formatAmount(payment.amount)} {payment.currency}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={payment.status} />
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">{formatDateTime(payment.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default PaymentListPage
