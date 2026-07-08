import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { createRefund, getPayment, getPaymentLedger } from '../api/payments'
import { formatAmount, formatDateTime, formatStatus } from '../lib/format'
import { getErrorMessage } from '../lib/apiError'
import StatusBadge from '../components/StatusBadge'

const REFUNDABLE_STATUSES = new Set(['CAPTURED', 'PARTIALLY_REFUNDED'])

function PaymentDetailPage() {
  const { organizationId, paymentId } = useParams<{ organizationId: string; paymentId: string }>()
  const queryClient = useQueryClient()

  const detailQuery = useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'payments', paymentId],
    queryFn: () => getPayment(organizationId!, paymentId!),
    enabled: Boolean(organizationId && paymentId),
  })

  const ledgerQuery = useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'payments', paymentId, 'ledger'],
    queryFn: () => getPaymentLedger(organizationId!, paymentId!),
    enabled: Boolean(organizationId && paymentId),
  })

  if (detailQuery.isPending) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading payment…</p>
  }

  if (detailQuery.isError) {
    return <p className="text-sm text-red-600 dark:text-red-400">Couldn't load this payment.</p>
  }

  const { payment, transitions } = detailQuery.data

  return (
    <div className="space-y-6">
      <div>
        <Link to=".." relative="path" className="text-sm text-indigo-600 hover:underline dark:text-indigo-400">
          ← Back to payments
        </Link>
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
        <div className="flex items-start justify-between">
          <div>
            <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-50">{payment.description ?? payment.id}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">{payment.id}</p>
          </div>
          <StatusBadge status={payment.status} />
        </div>
        <dl className="mt-4 grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
          <div>
            <dt className="text-gray-500 dark:text-gray-400">Amount</dt>
            <dd className="font-medium text-gray-900 dark:text-gray-100">
              {formatAmount(payment.amount)} {payment.currency}
            </dd>
          </div>
          <div>
            <dt className="text-gray-500 dark:text-gray-400">Captured</dt>
            <dd className="font-medium text-gray-900 dark:text-gray-100">{formatAmount(payment.capturedAmount)}</dd>
          </div>
          <div>
            <dt className="text-gray-500 dark:text-gray-400">Refunded</dt>
            <dd className="font-medium text-gray-900 dark:text-gray-100">{formatAmount(payment.refundedAmount)}</dd>
          </div>
          <div>
            <dt className="text-gray-500 dark:text-gray-400">Provider reference</dt>
            <dd className="font-medium text-gray-900 dark:text-gray-100">{payment.providerReference ?? '—'}</dd>
          </div>
        </dl>
      </div>

      {REFUNDABLE_STATUSES.has(payment.status) && (
        <RefundForm
          organizationId={organizationId!}
          paymentId={paymentId!}
          remainingAmount={payment.capturedAmount - payment.refundedAmount}
          currency={payment.currency}
          onRefunded={() => {
            queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'dashboard', 'payments', paymentId] })
            queryClient.invalidateQueries({
              queryKey: ['organizations', organizationId, 'dashboard', 'payments', paymentId, 'ledger'],
            })
          }}
        />
      )}

      <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
        <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-50">State timeline</h3>
        <ol className="space-y-2">
          {transitions.map((transition, index) => (
            <li key={index} className="flex items-center justify-between text-sm">
              <span className="text-gray-600 dark:text-gray-300">
                {transition.fromStatus ? formatStatus(transition.fromStatus) : 'Start'} → {formatStatus(transition.toStatus)}
                <span className="ml-2 text-gray-400">({transition.actor})</span>
              </span>
              <span className="text-gray-400">{formatDateTime(transition.createdAt)}</span>
            </li>
          ))}
        </ol>
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
        <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-50">Ledger entries</h3>
        {ledgerQuery.isPending && <p className="text-sm text-gray-500 dark:text-gray-400">Loading ledger…</p>}
        {ledgerQuery.isError && <p className="text-sm text-red-600 dark:text-red-400">Couldn't load ledger entries.</p>}
        {ledgerQuery.data && ledgerQuery.data.length === 0 && (
          <p className="text-sm text-gray-500 dark:text-gray-400">No ledger entries yet.</p>
        )}
        {ledgerQuery.data && ledgerQuery.data.length > 0 && (
          <table className="min-w-full text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
                <th className="py-1 pr-4">Account</th>
                <th className="py-1 pr-4">Type</th>
                <th className="py-1 pr-4">Amount</th>
                <th className="py-1">Recorded</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {ledgerQuery.data.map((entry) => (
                <tr key={entry.id}>
                  <td className="py-2 pr-4 text-gray-700 dark:text-gray-300">{entry.accountCode}</td>
                  <td className="py-2 pr-4 text-gray-700 dark:text-gray-300">{entry.entryType}</td>
                  <td className="py-2 pr-4 text-gray-900 dark:text-gray-100">
                    {formatAmount(entry.amount)} {entry.currency}
                  </td>
                  <td className="py-2 text-gray-400">{formatDateTime(entry.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

function RefundForm({
  organizationId,
  paymentId,
  remainingAmount,
  currency,
  onRefunded,
}: {
  organizationId: string
  paymentId: string
  remainingAmount: number
  currency: string
  onRefunded: () => void
}) {
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [success, setSuccess] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => createRefund(organizationId, paymentId, amount ? Number(amount) : undefined, reason || undefined),
    onSuccess: (refund) => {
      setSuccess(`Refund of ${formatAmount(refund.amount)} ${refund.currency} succeeded.`)
      setAmount('')
      setReason('')
      onRefunded()
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSuccess(null)
    mutation.reset()
    mutation.mutate()
  }

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
      <h3 className="mb-1 text-sm font-semibold text-gray-900 dark:text-gray-50">Issue a refund</h3>
      <p className="mb-3 text-sm text-gray-500 dark:text-gray-400">
        Up to {formatAmount(remainingAmount)} {currency} remaining refundable.
      </p>
      <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-3">
        <div>
          <label htmlFor="refund-amount" className="block text-xs font-medium text-gray-500 dark:text-gray-400">
            Amount (optional — full remaining balance if left blank)
          </label>
          <input
            id="refund-amount"
            type="number"
            step="0.01"
            min="0.01"
            max={remainingAmount}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder={formatAmount(remainingAmount)}
            className="mt-1 w-40 rounded-md border border-gray-300 px-3 py-1.5 text-sm dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
          />
        </div>
        <div>
          <label htmlFor="refund-reason" className="block text-xs font-medium text-gray-500 dark:text-gray-400">
            Reason (optional)
          </label>
          <input
            id="refund-reason"
            type="text"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="mt-1 w-56 rounded-md border border-gray-300 px-3 py-1.5 text-sm dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
          />
        </div>
        <button
          type="submit"
          disabled={mutation.isPending}
          className="rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-500 disabled:opacity-60"
        >
          {mutation.isPending ? 'Refunding…' : 'Refund'}
        </button>
      </form>
      {mutation.isError && (
        <p className="mt-2 text-sm text-red-600 dark:text-red-400">
          {getErrorMessage(mutation.error, "Couldn't process this refund.")}
        </p>
      )}
      {success && <p className="mt-2 text-sm text-green-600 dark:text-green-400">{success}</p>}
    </div>
  )
}

export default PaymentDetailPage
