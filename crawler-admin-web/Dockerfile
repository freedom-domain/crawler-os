FROM node:20-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine

ENV API_UPSTREAM=http://crawler-gateway:8080
ENV NGINX_ENVSUBST_FILTER=^API_UPSTREAM$

COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=builder /app/dist /usr/share/nginx/html
COPY --from=builder /app/public/crawleros-icon.svg /usr/share/nginx/html/favicon.svg

EXPOSE 80
