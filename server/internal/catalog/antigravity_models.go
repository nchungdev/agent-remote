package catalog

import (
	"context"
	"fmt"
	"os/exec"
	"strings"
	"sync"
	"time"
)

const agyModelsTTL = 10 * time.Minute

// agyModels runs `agy models` (the CLI queries the live model list for the signed-in account) and
// caches the result, since it needs the network. A stale list is served if a refresh fails.
type agyModels struct {
	mu      sync.Mutex
	models  []Model
	fetched time.Time
}

func (m *agyModels) get(ctx context.Context, bin string) ([]Model, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.models != nil && time.Since(m.fetched) < agyModelsTTL {
		return m.models, nil
	}
	out, err := exec.CommandContext(ctx, bin, "models").Output()
	if err != nil {
		return m.models, fmt.Errorf("agy models: %w", err)
	}
	parsed := parseAgyModels(string(out))
	if len(parsed) == 0 {
		return m.models, fmt.Errorf("agy models: empty output")
	}
	m.models, m.fetched = parsed, time.Now()
	return parsed, nil
}

// parseAgyModels reads "<id>\t<label>" lines; progress lines without a tab are ignored.
func parseAgyModels(out string) []Model {
	var models []Model
	for _, line := range strings.Split(out, "\n") {
		id, label, ok := strings.Cut(strings.TrimSpace(line), "\t")
		if !ok || id == "" {
			continue
		}
		models = append(models, Model{ID: id, Label: strings.TrimSpace(label)})
	}
	return models
}
