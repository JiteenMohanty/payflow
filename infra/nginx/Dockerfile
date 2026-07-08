# Build context is the repo root (this image needs both frontend/admin-dashboard
# and infra/nginx/nginx.conf, which share no common ancestor closer than that).
FROM node:20-alpine AS build
WORKDIR /build

COPY frontend/admin-dashboard/package.json frontend/admin-dashboard/package-lock.json ./
RUN npm ci

COPY frontend/admin-dashboard/ ./
# Relative path, not an absolute domain - correct for any deployment,
# since Nginx (this same image) proxies /grafana/ on whatever host/domain
# it's actually reached at. Overridable via --build-arg for a topology
# where Grafana isn't proxied through this Nginx.
ARG VITE_GRAFANA_URL=/grafana
ENV VITE_GRAFANA_URL=$VITE_GRAFANA_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY infra/nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80 443
