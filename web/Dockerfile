FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

# Unprivileged nginx (listens on 8080, runs as uid 101). env.js is written at start so one image fits every environment.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/web/browser /usr/share/nginx/html
USER root
RUN chown -R 101 /usr/share/nginx/html
USER 101
ENV API_URL=http://localhost:8080
EXPOSE 8080
CMD ["/bin/sh", "-c", "echo \"window.__env = { apiUrl: '${API_URL}' };\" > /usr/share/nginx/html/env.js && exec nginx -g 'daemon off;'"]
