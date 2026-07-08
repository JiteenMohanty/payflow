import { formatStatus } from '../lib/format'

const statusColors: Record<string, string> = {
  CREATED: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  AUTHORIZED: 'bg-blue-100 text-blue-700 dark:bg-blue-500/10 dark:text-blue-300',
  CAPTURED: 'bg-green-100 text-green-700 dark:bg-green-500/10 dark:text-green-300',
  PARTIALLY_REFUNDED: 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300',
  REFUNDED: 'bg-purple-100 text-purple-700 dark:bg-purple-500/10 dark:text-purple-300',
  FAILED: 'bg-red-100 text-red-700 dark:bg-red-500/10 dark:text-red-300',
  EXPIRED: 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
  PENDING: 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300',
  SUCCEEDED: 'bg-green-100 text-green-700 dark:bg-green-500/10 dark:text-green-300',
  EXHAUSTED: 'bg-red-100 text-red-700 dark:bg-red-500/10 dark:text-red-300',
  ACTIVE: 'bg-green-100 text-green-700 dark:bg-green-500/10 dark:text-green-300',
  DISABLED: 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
}

function StatusBadge({ status }: { status: string }) {
  const colorClass = statusColors[status] ?? statusColors.CREATED
  return <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${colorClass}`}>{formatStatus(status)}</span>
}

export default StatusBadge
