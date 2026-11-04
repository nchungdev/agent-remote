//go:build windows

package adapter

import "os/exec"

func configureProcessGroup(*exec.Cmd) {}
