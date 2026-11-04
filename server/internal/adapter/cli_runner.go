package adapter

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"app.duinch.agentremote/server/internal/catalog"
	"app.duinch.agentremote/server/internal/protocol"
)

const (
	maxCLILineBytes  = 64 * 1024 * 1024
	cliExitGrace     = 3 * time.Second
	stderrTailLength = 8 * 1024
)

// cliRunner runs one agent CLI prompt at a time for an adapter.
type cliRunner struct {
	mu     sync.Mutex
	active *cliRun
}

// begin supersedes an unfinished prompt: its later events must not reach the
// client, where they would end the new prompt's message.
func (r *cliRunner) begin(emit Emitter, msgID string) *cliRun {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.active != nil {
		r.active.supersede()
	}
	ctx, cancel := context.WithCancel(context.Background())
	r.active = &cliRun{ctx: ctx, cancel: cancel, send: emit, msgID: msgID}
	return r.active
}

// Cancel stops the unfinished prompt, if any.
func (r *cliRunner) Cancel() {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.active != nil && !r.active.isFinished() {
		r.active.cancel()
	}
}

// HandleApproval: agent CLIs run non-interactively and never wait on an answer, so
// a denial can only stop the task.
func (r *cliRunner) HandleApproval(_ string, approved bool) {
	if !approved {
		r.Cancel()
	}
}

func (r *cliRunner) Close() {
	r.Cancel()
}

// cliRun is one prompt's lifetime; it ends the client's message exactly once.
type cliRun struct {
	ctx    context.Context
	cancel context.CancelFunc
	send   Emitter
	msgID  string

	mu             sync.Mutex
	finished       bool
	conversationID string
}

func (c *cliRun) isFinished() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.finished
}

func (c *cliRun) supersede() {
	c.mu.Lock()
	wasFinished := c.finished
	c.finished = true
	c.mu.Unlock()
	if !wasFinished {
		c.cancel()
	}
}

func (c *cliRun) emit(event []byte) {
	if !c.isFinished() {
		c.send(event)
	}
}

func (c *cliRun) setConversationID(id string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if id != "" {
		c.conversationID = id
	}
}

func (c *cliRun) finish() {
	c.mu.Lock()
	if c.finished {
		c.mu.Unlock()
		return
	}
	c.finished = true
	conversationID := c.conversationID
	c.mu.Unlock()
	c.send(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
	c.send(protocol.MessageEnd(c.msgID, conversationID))
}

func (c *cliRun) fail(message string) {
	c.emit(protocol.ErrorEvent(message))
	c.finish()
}

// execute runs the CLI, feeding each stdout line to onLine. Stdin stays closed:
// `codex exec` would otherwise wait on it forever.
func (c *cliRun) execute(engine, bin string, args []string, dir string, onLine func(line []byte)) {
	defer c.cancel()
	cmd := exec.CommandContext(c.ctx, bin, args...)
	cmd.Dir = dir
	cmd.WaitDelay = cliExitGrace
	configureProcessGroup(cmd)
	stderr := &tailBuffer{limit: stderrTailLength}
	cmd.Stderr = stderr

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		c.fail(fmt.Sprintf("Không thể mở stdout của %s: %v", engine, err))
		return
	}
	if err := cmd.Start(); err != nil {
		c.fail(fmt.Sprintf("Không thể khởi động %s: %v", engine, err))
		return
	}

	scanner := bufio.NewScanner(stdout)
	scanner.Buffer(make([]byte, 256*1024), maxCLILineBytes)
	for scanner.Scan() {
		onLine(scanner.Bytes())
	}
	scanErr := scanner.Err()
	if scanErr != nil {
		c.cancel() // Unread output would block the CLI and Wait forever.
	}
	waitErr := cmd.Wait()

	switch {
	case scanErr != nil && !errors.Is(scanErr, os.ErrClosed):
		c.fail(fmt.Sprintf("Không đọc được output của %s: %v", engine, scanErr))
	case c.ctx.Err() != nil:
		c.finish()
	case waitErr != nil:
		c.fail(cliFailureMessage(engine, stderr.String(), waitErr))
	default:
		c.finish()
	}
}

func cliFailureMessage(engine, stderr string, waitErr error) string {
	detail := strings.TrimSpace(strings.ReplaceAll(stderr, "Reading additional input from stdin...", ""))
	if detail == "" {
		detail = waitErr.Error()
	}
	return fmt.Sprintf("%s lỗi: %s", engine, detail)
}

// tailBuffer keeps the end of a stream, where CLIs print the actual failure.
type tailBuffer struct {
	mu    sync.Mutex
	limit int
	data  []byte
}

func (t *tailBuffer) Write(p []byte) (int, error) {
	t.mu.Lock()
	defer t.mu.Unlock()
	t.data = append(t.data, p...)
	if len(t.data) > t.limit {
		t.data = t.data[len(t.data)-t.limit:]
	}
	return len(p), nil
}

func (t *tailBuffer) String() string {
	t.mu.Lock()
	defer t.mu.Unlock()
	return string(t.data)
}

// safeFlagValue rejects client values a CLI would parse as one of its own flags,
// such as a model of "--dangerously-bypass-approvals-and-sandbox".
func safeFlagValue(value string) bool {
	return !strings.HasPrefix(strings.TrimSpace(value), "-")
}

func validateOptions(opts PromptOptions) error {
	if !safeFlagValue(opts.Model) || !safeFlagValue(opts.ConversationID) {
		return errors.New("Model hoặc mã hội thoại không hợp lệ")
	}
	return nil
}

// workingDir avoids running agents at the filesystem root, the cwd of a menu-bar app.
func workingDir(requested string) string {
	if requested != "" {
		return requested
	}
	if cwd, err := os.Getwd(); err == nil && cwd != string(filepath.Separator) {
		return cwd
	}
	home, _ := os.UserHomeDir()
	return home
}

func findCLI(name string) string {
	home, _ := os.UserHomeDir()
	return catalog.FindBinary(home, name)
}
