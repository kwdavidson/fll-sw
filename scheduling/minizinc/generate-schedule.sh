#!/bin/sh

mydir=$(cd "$(dirname "$0")" && pwd -L) || exit 1

debug() { ! "${log_debug-false}" || log "DEBUG: $*" >&2; }
log() { printf '%s\n' "$*"; }
warn() { log "WARNING: $*" >&2; }
error() { log "ERROR: $*" >&2; }
fatal() { error "$*"; exit 1; }

if [ $# -ne 1 ]; then
    fatal "Usage: $0 <params.dzn>"
fi

param_file=$1
mzn2fzn --no-output-ozn --globals-dir linear "${mydir}/schedule.mzn" "${param_file}" -o "${param_file}.fzn" || fatal "Error executing mzn2fzn"
flatzinc -b mip -o "${param_file}.result" "${param_file}.fzn" || fatal "Error executing flatzinc"
rm -f "${param_file}.fzn"
log "Result is in ${param_file}.result"