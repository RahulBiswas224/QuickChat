#!/bin/bash
# ─────────────────────────────────────────────
#  QuickChat — build & run helper
#  Usage:
#    ./run.sh server       → start the server
#    ./run.sh client       → start a client (open multiple terminals!)
#    ./run.sh compile      → compile only
# ─────────────────────────────────────────────

SRC="src"
OUT="out"

compile() {
    echo "Compiling..."
    mkdir -p $OUT
    javac -d $OUT $(find $SRC -name "*.java")
    if [ $? -eq 0 ]; then
        echo "Compiled successfully → $OUT/"
    else
        echo "Compilation failed!"
        exit 1
    fi
}

case "$1" in
    server)
        compile
        echo "Starting server..."
        java -cp $OUT server.ChatServer
        ;;
    client)
        compile
        echo "Starting CLI client..."
        java -cp $OUT client.ChatClient
        ;;
    gui)
        compile
        echo "Starting GUI client..."
        java -cp $OUT client.ChatClientGUI
        ;;
    compile)
        compile
        ;;
    *)
        echo "Usage: ./run.sh [server|client|gui|compile]"
        ;;
esac