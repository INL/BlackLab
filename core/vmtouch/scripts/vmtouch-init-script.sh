#!/bin/sh
### BEGIN INIT INFO
# Provides:          vmtouch
# Required-Start:    $remote_fs $syslog
# Required-Stop:     $remote_fs $syslog
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Start vmtouch at boot time
# Description:       Keeps certain files locked in the disk cache.
### END INIT INFO

# (Adapted from template at https://github.com/fhd/init-script-template/)

# Settings: dir to run from, user to run under, command to run
dir="/blacklab/bin/vmtouch"
user="root"
cmd="/blacklab/bin/vmtouch/activateVmtouch.sh"

# Daemon's name
name=`basename $0`

# Log files
stdout_log="/var/log/$name.log"
stderr_log="/var/log/$name.err"

# If vmtouch is running, return its PID.
get_pid() {
    echo `ps -ef | grep -v grep | grep "vmtouch -vtld" | awk '{print $2}' `
}

# Check to see if vmtouch is running.
is_running() {
    ps -ef | grep -v grep | grep "vmtouch -vtld" >/dev/null
}

# Start, stop, restart or show status of vmtouch, depending on commandline
case "$1" in
    start)
    if is_running; then
        echo "Already started"
    else
        echo "Starting $name"
        cd "$dir"
        sudo -u "$user" $cmd >> "$stdout_log" 2>> "$stderr_log"
        if ! is_running; then
            echo "Unable to start, see $stdout_log and $stderr_log"
            exit 1
        fi
    fi
    ;;
    stop)
    if is_running; then
        echo -n "Stopping $name.."
        kill `get_pid`
        for i in {1..10}
        do
            if ! is_running; then
                break
            fi

            echo -n "."
            sleep 1
        done
        echo

        if is_running; then
            echo "Not stopped; may still be shutting down or shutdown may have failed"
            exit 1
        else
            echo "Stopped"
        fi
    else
        echo "Not running"
    fi
    ;;
    restart)
    $0 stop
    if is_running; then
        echo "Unable to stop, will not attempt to start"
        exit 1
    fi
    $0 start
    ;;
    status)
    if is_running; then
        echo "Running"
    else
        echo "Stopped"
        exit 1
    fi
    ;;
    *)
    echo "Usage: $0 {start|stop|restart|status}"
    exit 1
    ;;
esac

exit 0