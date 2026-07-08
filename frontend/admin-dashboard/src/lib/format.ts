const numberFormatter = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const countFormatter = new Intl.NumberFormat('en-US')
const dateFormatter = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' })

export function formatAmount(amount: number): string {
  return numberFormatter.format(amount)
}

export function formatCount(count: number): string {
  return countFormatter.format(count)
}

export function formatDateTime(isoTimestamp: string): string {
  return dateFormatter.format(new Date(isoTimestamp))
}

const statusLabels: Record<string, string> = {
  CREATED: 'Created',
  AUTHORIZED: 'Authorized',
  CAPTURED: 'Captured',
  PARTIALLY_REFUNDED: 'Partially refunded',
  REFUNDED: 'Refunded',
  FAILED: 'Failed',
  EXPIRED: 'Expired',
  PENDING: 'Pending',
  SUCCEEDED: 'Succeeded',
  EXHAUSTED: 'Exhausted',
  ACTIVE: 'Active',
  DISABLED: 'Disabled',
}

export function formatStatus(status: string): string {
  return statusLabels[status] ?? status
}

const windowLabels: Record<string, string> = {
  '24h': 'Last 24 hours',
  '7d': 'Last 7 days',
  '30d': 'Last 30 days',
}

export function formatWindow(window: string): string {
  return windowLabels[window] ?? window
}
