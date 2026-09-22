# ---------------------------------------------------------------- build
FROM node:22-alpine AS build
WORKDIR /build

# package*.json primeiro: a camada de node_modules so e refeita quando as
# dependencias mudam, nao a cada alteracao de componente.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------------------------------------------------------------- runtime
# O build do Vite e HTML/CSS/JS estatico — nao precisa de Node em producao.
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
# 127.0.0.1 e nao localhost: dentro do container, localhost resolve primeiro para
# ::1 (IPv6) e o nginx desta imagem so escuta em IPv4 — o healthcheck falharia com
# "connection refused" com o site funcionando perfeitamente.
HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
