# Build Stage
FROM golang:1.24-alpine AS builder

WORKDIR /build
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o agent-remote-server ./cmd/server

# Final Ultra-Lightweight Image (< 15MB)
FROM alpine:3.20

RUN apk --no-cache add ca-certificates git

WORKDIR /app
COPY --from=builder /build/agent-remote-server /app/agent-remote-server

EXPOSE 8765

ENTRYPOINT ["/app/agent-remote-server"]
CMD ["--host", "0.0.0.0", "--port", "8765"]
