# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

COPY . .

# The SPA talks to the backend through the Nginx reverse proxy, so a relative
# base URL ("/") is used by default. It can be overridden at build time.
ARG VITE_API_BASE_URL=/
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# ---------- Runtime stage ----------
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
