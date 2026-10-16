FROM node:22.18.0-alpine3.22 AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.29.1-alpine3.22
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/dispute-portal/browser /usr/share/nginx/html
EXPOSE 80
