FROM golang:1.21-alpine AS build
WORKDIR /src
COPY go.mod .
COPY cmd cmd
RUN CGO_ENABLED=0 go build -o /products-api ./cmd/api

FROM alpine:3.20
RUN adduser -D -u 10001 appuser
USER appuser
ENV PORT=8081
COPY --from=build /products-api /products-api
EXPOSE 8081
ENTRYPOINT ["/products-api"]
