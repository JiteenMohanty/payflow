import { createBrowserRouter } from 'react-router-dom'
import LoginPage from '../pages/LoginPage'
import RootPage from '../pages/RootPage'
import DashboardSummaryPage from '../pages/DashboardSummaryPage'
import PaymentListPage from '../pages/PaymentListPage'
import PaymentDetailPage from '../pages/PaymentDetailPage'
import WebhookEndpointsPage from '../pages/WebhookEndpointsPage'
import WebhookDeliveriesPage from '../pages/WebhookDeliveriesPage'
import MetricsPage from '../pages/MetricsPage'
import ProtectedRoute from './ProtectedRoute'
import DashboardLayout from './DashboardLayout'

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <RootPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/organizations/:organizationId/dashboard',
    element: (
      <ProtectedRoute>
        <DashboardLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <DashboardSummaryPage /> },
      { path: 'payments', element: <PaymentListPage /> },
      { path: 'payments/:paymentId', element: <PaymentDetailPage /> },
      { path: 'webhooks', element: <WebhookEndpointsPage /> },
      { path: 'webhooks/:endpointId', element: <WebhookDeliveriesPage /> },
      { path: 'metrics', element: <MetricsPage /> },
    ],
  },
])
