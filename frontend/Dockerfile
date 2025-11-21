FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
# Install dependencies (fall back to npm install even if lock is not present)
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY . .
RUN npm run build

FROM node:20-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY --from=deps /app ./
EXPOSE 3000
CMD ["npm", "run", "start"]
