package gateway

import (
	"crypto/rand"
	"encoding/hex"
	"net/http/httptest"
	"testing"
)

func TestFeatureWebSocketAuthentication(t *testing.T) {
	b := make([]byte, 24)
	_, _ = rand.Read(b)
	token := hex.EncodeToString(b)
	s := NewServer("127.0.0.1", 18765, token)
	for _, c := range []struct {
		name, query, header string
		status              int
	}{
		{"missing", "", "", 401}, {"invalid", "?token=invalid", "", 401},
		{"query-valid", "?token=" + token, "", 400}, {"bearer-valid", "", "Bearer " + token, 400},
	} {
		t.Run(c.name, func(t *testing.T) {
			r := httptest.NewRequest("GET", "/ws"+c.query, nil)
			if c.header != "" {
				r.Header.Set("Authorization", c.header)
			}
			w := httptest.NewRecorder()
			s.HandleWS(w, r)
			// Valid token reaches WebSocket upgrade, which returns 400 for this plain HTTP request.
			if w.Code != c.status {
				t.Fatalf("status=%d want=%d", w.Code, c.status)
			}
		})
	}
}
