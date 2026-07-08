const GRAFANA_URL = import.meta.env.VITE_GRAFANA_URL ?? 'http://localhost:3000'
const DASHBOARD_SRC = `${GRAFANA_URL}/d/payflow-overview/payflow-overview?orgId=1&kiosk`

function MetricsPage() {
  return (
    <div>
      <h2 className="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-50">Live metrics</h2>
      <div className="overflow-hidden rounded-lg border border-gray-200 dark:border-gray-800">
        <iframe
          title="PayFlow Overview (Grafana)"
          src={DASHBOARD_SRC}
          className="h-[1400px] w-full"
          referrerPolicy="no-referrer-when-downgrade"
        />
      </div>
    </div>
  )
}

export default MetricsPage
