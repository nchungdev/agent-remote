package gateway

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/nchungdev/remote-tunnel/go/signaling"
)

type SignalHTTPHandler struct {
	hub   *signaling.MessageHub
	token string
}

func NewSignalHTTPHandler(hub *signaling.MessageHub, authToken string) *SignalHTTPHandler {
	return &SignalHTTPHandler{
		hub:   hub,
		token: strings.TrimSpace(authToken),
	}
}

func (h *SignalHTTPHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if h.token != "" {
		auth := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
		if auth == "" {
			auth = r.URL.Query().Get("token")
		}
		if auth != h.token {
			http.Error(w, "Unauthorized signal access", http.StatusUnauthorized)
			return
		}
	}

	room := r.URL.Query().Get("room")
	if room == "" {
		room = "default"
	}
	peer := r.URL.Query().Get("peer")
	if peer == "" {
		peer = "client"
	}

	switch r.Method {
	case http.MethodPost:
		var msg signaling.SignalMessage
		if err := json.NewDecoder(r.Body).Decode(&msg); err != nil {
			http.Error(w, "Invalid signal JSON", http.StatusBadRequest)
			return
		}
		msg.Sender = peer
		h.hub.Broadcast(room, msg)
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{
			"status": "delivered",
		})

	case http.MethodGet:
		// Long-polling pull endpoint: wait up to 25s for incoming signal
		ch, err := h.hub.Register(room, peer)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		defer h.hub.Unregister(room, peer)

		select {
		case msg, ok := <-ch:
			if !ok {
				http.Error(w, "Session terminated", http.StatusGone)
				return
			}
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(msg)

		case <-time.After(25 * time.Second):
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusNoContent)

		case <-r.Context().Done():
			return
		}

	default:
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
	}
}
