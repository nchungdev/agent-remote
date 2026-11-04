//go:build !windows

package adapter

import (
	"os/exec"
	"syscall"
)

// configureProcessGroup makes cancel also stop the commands an agent started.
func configureProcessGroup(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	cmd.Cancel = func() error {
		return syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL)
	}
}
