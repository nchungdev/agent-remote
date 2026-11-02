package catalog

import (
	"database/sql"
	"fmt"
	"net/url"
	"os"
	"strings"
	"unicode/utf8"

	_ "modernc.org/sqlite"
)

// openReadOnly opens an engine database without ever taking a write lock, so reading never blocks
// or corrupts the engine that owns it. WAL files are honoured, so fresh rows are visible.
func openReadOnly(path string) (*sql.DB, error) {
	if _, err := os.Stat(path); err != nil {
		return nil, err
	}
	dsn := fmt.Sprintf("file:%s?mode=ro&_pragma=busy_timeout(3000)", (&url.URL{Path: path}).EscapedPath())
	return sql.Open("sqlite", dsn)
}

// clip shortens s to n runes on a rune boundary and collapses whitespace to single spaces.
func clip(s string, n int) string {
	s = strings.Join(strings.Fields(s), " ")
	if utf8.RuneCountInString(s) <= n {
		return s
	}
	r := []rune(s)
	return string(r[:n]) + "…"
}
