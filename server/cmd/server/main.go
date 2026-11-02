package main

import (
	"flag"
	"log"
	"os"
	"os/signal"
	"runtime"
	"syscall"

	"app.duinch.agentremote/server/internal/gateway"
	"app.duinch.agentremote/server/internal/tray"
)

func main() {
	host := flag.String("host", "0.0.0.0", "Host address to bind")
	port := flag.Int("port", 8765, "Port to listen on")
	token := flag.String("token", "", "Optional authentication token")
	headless := flag.Bool("headless", false, "Run in headless CLI mode without tray icon")
	flag.Parse()

	srv := gateway.NewServer(*host, *port, *token)

	// If macOS and not headless -> run as Menu Bar App!
	if runtime.GOOS == "darwin" && !*headless {
		// Start gateway in background
		go func() {
			if err := srv.Start(); err != nil {
				log.Printf("[Gateway] Lỗi chạy server: %v", err)
			}
		}()

		trayMgr := tray.NewTrayManager(srv)
		trayMgr.Run()
		return
	}

	// Headless CLI mode
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, os.Interrupt, syscall.SIGTERM)

	go func() {
		<-sigCh
		log.Println("[Gateway] Gateway đã nhận tín hiệu dừng, đang tắt...")
		_ = srv.Stop()
		os.Exit(0)
	}()

	if err := srv.Start(); err != nil {
		log.Fatalf("[Gateway] Lỗi chạy server: %v", err)
	}
}
