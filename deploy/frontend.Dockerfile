ARG BASE_IMAGE=node:24-alpine
# next.config.ts rewrites는 build 시점에 routes-manifest에 고정된다. runtime 값도 같게 유지한다.
ARG BACKEND_URL=http://backend:8080
FROM ${BASE_IMAGE} AS build
ARG BACKEND_URL
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1 BACKEND_URL=${BACKEND_URL}
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM ${BASE_IMAGE}
ARG BACKEND_URL
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0 PORT=3000 BACKEND_URL=${BACKEND_URL} NODE_OPTIONS=--max-old-space-size=256
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
