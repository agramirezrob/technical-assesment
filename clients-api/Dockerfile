FROM node:20-alpine AS build
WORKDIR /app
COPY package.json .
RUN npm install
COPY nest-cli.json tsconfig.json tsconfig.build.json .
COPY src src
RUN npm run build

FROM node:20-alpine
WORKDIR /app
ENV PORT=8082
COPY package.json .
RUN npm install --omit=dev
COPY --from=build /app/dist dist
EXPOSE 8082
CMD ["npm", "run", "start:prod"]
