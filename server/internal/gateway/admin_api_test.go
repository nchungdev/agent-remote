package gateway

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestAdminAPIStatus(t *testing.T) {
	srv := NewServer("127.0.0.1", 8765, "")
	req := httptest.NewRequest("GET", "/api/status", nil)
	w := httptest.NewRecorder()

	srv.HandleAPIStatus(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d", w.Code)
	}

	var status StatusResponse
	if err := json.Unmarshal(w.Body.Bytes(), &status); err != nil {
		t.Fatalf("invalid json: %v", err)
	}

	if status.Port != 8765 {
		t.Fatalf("expected port 8765, got %d", status.Port)
	}
}

func TestAdminAPIClients(t *testing.T) {
	srv := NewServer("127.0.0.1", 8765, "")
	req := httptest.NewRequest("GET", "/api/clients", nil)
	w := httptest.NewRecorder()

	srv.HandleAPIClients(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d", w.Code)
	}

	var clients []ClientInfo
	if err := json.Unmarshal(w.Body.Bytes(), &clients); err != nil {
		t.Fatalf("invalid json: %v", err)
	}
	if len(clients) != 0 {
		t.Fatalf("expected 0 clients initially, got %d", len(clients))
	}
}

func TestAdminAPILogs(t *testing.T) {
	srv := NewServer("127.0.0.1", 8765, "")
	req := httptest.NewRequest("GET", "/api/logs", nil)
	w := httptest.NewRecorder()

	srv.HandleAPILogs(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d", w.Code)
	}
}

func TestDashboardHTML(t *testing.T) {
	srv := NewServer("127.0.0.1", 8765, "")
	req := httptest.NewRequest("GET", "/dashboard", nil)
	w := httptest.NewRecorder()

	srv.HandleDashboard(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d", w.Code)
	}
	body := w.Body.String()
	if !httptest.NewRecorder().Flushed && len(body) < 100 {
		t.Fatalf("dashboard html too small: %d bytes", len(body))
	}
}
