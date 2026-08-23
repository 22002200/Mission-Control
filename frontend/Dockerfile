# syntax=docker/dockerfile:1

# =============================================================================
# deps - node_modules live in the image, never on a host bind mount.
#
# This is what makes `docker compose up --watch` work cleanly on Windows: Compose
# syncs only ./src into the container, so the host filesystem never shadows
# node_modules and native binaries stay Linux-built.
# =============================================================================
FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

# =============================================================================
# dev - what `docker compose up` runs.
# =============================================================================
FROM node:24-alpine AS dev
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]

# =============================================================================
# build - type-check and produce static assets.
# =============================================================================
FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# =============================================================================
# runtime - nginx serving the built SPA, used by compose.prod.yaml.
# =============================================================================
FROM nginx:1.29-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
