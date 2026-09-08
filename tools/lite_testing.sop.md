# Run Greengrass Lite UAT Against an Upstream Feature Branch

## Overview

This SOP dispatches and monitors the `aws-greengrass-testing` UAT workflow
(`uat.yml`) against a feature branch in the upstream
`aws-greengrass/aws-greengrass-lite` repository, using only the GitHub CLI
(`gh`). Every `gh` call names its target with `-R <owner>/<repo>` or an explicit
REST/GraphQL path, so no local clone or `git` command is needed.

Use it when a builder wants the shared UAT suite run against a lite branch (for
example `dev/ipc-connection-status`) without cloning anything. The workflow's
`lite_repo` input is pinned to the upstream lite repo, so the branch under test
MUST resolve there — fork-only branches are rejected.

**Scope boundary (the safety contract).** The transaction performs exactly one
real CI mutation: one workflow dispatch in the selected mode. Everything else is
read-only. It never retries, switches modes, reruns, cancels, deletes, or
modifies runs or branches. Resolved parameters are passed as distinct positional
arguments and never interpolated into shell source or re-evaluated. After the
sole dispatch attempt, any uncertain result, discovery failure, or run-URL
failure enters finalization-only mode and still reaches the mandatory nonzero
summary without redispatching or inventing correlation data. A local monitor
timeout or an INT/TERM signal stops only the local monitor — never the remote
run.

## Parameters

- **lite_repo_ref** (required): Exact branch/ref in upstream
  `aws-greengrass/aws-greengrass-lite` (may contain `/`); fork-only refs are
  rejected.
- **testing_repo** (optional, default: `aws-greengrass/aws-greengrass-testing`):
  `owner/repo` containing `uat.yml`.
- **testing_ref** (optional, default: `main`): Branch holding the workflow
  definition, independent of the lite ref.
- **poll_interval_seconds** (optional, default: `5`): Positive integer seconds
  between read-only status calls.
- **monitor_mode** (optional, default: `watch`): `watch` streams with
  `gh run watch`; `poll` uses bounded JSON polling.
- **monitor_timeout_seconds** (optional, default: `10800`): Positive integer
  local deadline; expiry stops only local monitoring and exits nonzero.
- **dispatch_mode** (optional, default: `standard`): Primary `standard`
  (`gh workflow run`) or optional `deterministic` REST dispatch.
- **expected_lite_sha** (optional, default: empty): When non-empty, a 40-hex
  commit SHA the upstream branch MUST resolve to exactly before dispatch; empty
  means branch-only validation.

**Constraints for parameter acquisition:**

- If all required parameters are already provided, You MUST proceed to the Steps
- If any required parameters are missing, You MUST ask for them before
  proceeding
- When asking for parameters, You MUST request all parameters in a single prompt
- When asking for parameters, You MUST use the exact parameter names as defined
- You MUST NOT accept a `lite_repo` value from the user because this SOP fixes
  it to `aws-greengrass/aws-greengrass-lite` to reject fork-only refs
- You MUST resolve every optional default at this layer before invoking the
  transaction, because the script accepts only the final eight-value argument
  vector
- You SHOULD confirm resolved non-secret values before dispatch so an unintended
  ref or mode is caught before shared CI is consumed

## Execution Contract

The numbered Steps below describe one Bash **transaction** — the single script
in [Fixed Bash Transaction](#fixed-bash-transaction). You MUST run that script
verbatim, exactly once, with `/bin/bash` and eight distinct positional arguments
in this order:

```text
argv[1] = <temporary-script-path>
argv[2] = <resolved lite_repo_ref>
argv[3] = <resolved testing_repo>
argv[4] = <resolved testing_ref>
argv[5] = <resolved poll_interval_seconds>
argv[6] = <resolved monitor_mode>
argv[7] = <resolved monitor_timeout_seconds>
argv[8] = <resolved dispatch_mode>
argv[9] = <resolved expected_lite_sha or empty string>
```

- You MUST use an execution mechanism that preserves these as separate
  argument-vector entries. You MUST NOT flatten, script-embed, or
  `eval`/`source` them because shell parsing could reinterpret user-controlled
  ref text. If no argv-capable launcher is available, You MUST stop before
  dispatch and ask the user to run the fixed script safely.
- The script defines all guards, helpers, and traps once, then runs Steps 1–10
  in order (see the `# ===== STEP N =====` banners). You MUST NOT split it into
  per-step shells because later steps depend on earlier state and fail-closed
  guards.
- `DISPATCH_ATTEMPTED=true` is the sole irreversible boundary. Before it, any
  failure or signal exits without mutation. After it, no path exits before the
  Step 10 summary and no retry, mode switch, rerun, or cancel is permitted,
  because GitHub may have accepted the request before the client observed a
  result.
- You MUST delete the temporary script after execution and MUST NOT print or
  persist authentication tokens, because either could expose credentials.

## Fixed Bash Transaction

```bash
#!/usr/bin/env bash
# ONE transaction, invoked once with 8 positional args (see Execution Contract).
# Resolved values arrive ONLY as argv entries — never interpolated into this
# source and never eval'd — so user-controlled ref text cannot be reparsed as
# shell. Defaults are resolved by the caller and are not re-applied here.
set -euo pipefail

# --- Fixed by this SOP; NOT user-selectable (fork refs are rejected) ---
readonly LITE_REPO="aws-greengrass/aws-greengrass-lite"
readonly WORKFLOW_FILE="uat.yml"

# --- Eight resolved parameters, transported only as positional argv ---
[ "$#" -eq 8 ] || { echo "ERROR: expected 8 positional parameters; got $#" >&2; exit 2; }
readonly LITE_REPO_REF="$1"
readonly TESTING_REPO="$2"
readonly TESTING_REF="$3"
readonly POLL_INTERVAL_SECONDS="$4"
readonly MONITOR_MODE="$5"              # watch | poll
readonly MONITOR_TIMEOUT_SECONDS="$6"
readonly DISPATCH_MODE="$7"             # standard | deterministic
readonly EXPECTED_LITE_SHA="$8"         # empty, or a required 40-hex upstream SHA

# --- Fail-closed guards: only a passing preflight sets each true ---
GH_READY=false; AUTH_OK=false; PERMISSION_OK=false; WORKFLOW_OK=false
UPSTREAM_REF_OK=false; CONTRACT_OK=false; SNAPSHOT_OK=false
DISPATCH_ATTEMPTED=false   # SOLE irreversible marker of the one attempt
DISPATCH_EXECUTED=false    # true only after an observed successful dispatch

# --- Post-attempt state: once set, report but never redispatch or exit early ---
FINALIZE_ONLY=false; FINALIZATION_DIAGNOSTICS=""
INTERRUPTED=false; SIGNAL_NAME=""; SIGNAL_EXIT=""
ACTOR=""; LITE_SHA=""; LITE_REF_URL_PATH=""
PRE_RUN_ID=""; RUN_ID=""; RUN_URL=""; RUN_API_URL=""
STATUS=""; CONCLUSION=""; DISCOVERY_DIAGNOSTICS=""
OUTCOME="not-started"; RESULT_EXIT=1
ACTIVE_PID=""; TEMP_PATHS=()

is_positive_integer() { [[ "$1" =~ ^[1-9][0-9]*$ ]]; }

require_true() {  # Block dispatch unless a named guard is exactly "true".
  local n="$1" v="${!1:-false}"
  [ "$v" = "true" ] || { echo "ERROR: guard $n is not true; dispatch blocked" >&2; return 1; }
}

urlencode_ref_path() {  # Percent-encode a ref for a browser link, preserving '/'.
  local in="$1" out="" c i; LC_ALL=C
  for ((i = 0; i < ${#in}; i++)); do c="${in:i:1}"
    case "$c" in [A-Za-z0-9._~-]|/) out+="$c" ;; *) printf -v c '%%%02X' "'$c"; out+="$c" ;; esac
  done
  printf '%s' "$out"
}

enter_finalization_only() {
  # After the sole attempt, turn any failure into reportable state; never exit
  # early and never redispatch. Step 10 still prints the durable summary.
  OUTCOME="$1"; shift; RESULT_EXIT=1; FINALIZE_ONLY=true; FINALIZATION_DIAGNOSTICS="$*"
}

stop_active_pid() {  # Stop ONLY the tracked local gh process; never cancel the remote run.
  if [ -n "${ACTIVE_PID:-}" ] && kill -0 "$ACTIVE_PID" 2>/dev/null; then
    kill "$ACTIVE_PID" 2>/dev/null || true; wait "$ACTIVE_PID" 2>/dev/null || true
  fi
  ACTIVE_PID=""
}

cleanup_local_processes() {  # EXIT trap: local-only cleanup; touches no remote state.
  stop_active_pid
  [ "${#TEMP_PATHS[@]}" -eq 0 ] || rm -f -- "${TEMP_PATHS[@]}"
}

record_interruption() {
  # INT/TERM. Pre-dispatch: fail fast (nothing mutated, no summary owed).
  # Post-dispatch: stop only the local monitor, keep 130/143, and drain to the
  # Step 10 summary. Never cancels the remote run. First signal wins.
  local sig="$1"
  [ "$INTERRUPTED" = "true" ] && return 0
  INTERRUPTED=true; SIGNAL_NAME="$sig"
  case "$sig" in
    INT)  SIGNAL_EXIT=130; OUTCOME="interrupted-int" ;;
    TERM) SIGNAL_EXIT=143; OUTCOME="interrupted-term" ;;
    *)    SIGNAL_EXIT=1;   OUTCOME="interrupted-signal" ;;
  esac
  stop_active_pid
  if [ "$DISPATCH_ATTEMPTED" != "true" ]; then
    echo "WARN: SIG${sig} before dispatch; exiting ${SIGNAL_EXIT} (nothing was mutated)" >&2
    exit "$SIGNAL_EXIT"
  fi
  FINALIZE_ONLY=true; RESULT_EXIT="$SIGNAL_EXIT"
  FINALIZATION_DIAGNOSTICS="SIG${sig} after the attempt; stopped only the local monitor; the GitHub run was NOT cancelled and may still be active; do not retry/rerun/cancel"
}
trap cleanup_local_processes EXIT
trap 'record_interruption INT' INT
trap 'record_interruption TERM' TERM

sleep_within_deadline() {  # Sleep, clamped to the remaining deadline; tolerate signal.
  local deadline="$1" want="$2" left=$(( $1 - SECONDS ))
  [ "$left" -le 0 ] && return 1
  [ "$want" -gt "$left" ] && want="$left"
  sleep "$want" || true
}

capture_until_deadline() {
  # Run a read-only gh call in the background, capture stdout, enforce a local
  # deadline, and on timeout stop ONLY that local process (rc 124).
  local var="$1" deadline="$2" label="$3"; shift 3
  local out err pid rc
  out="$(mktemp -t gg-uat-out.XXXXXX)"; err="$(mktemp -t gg-uat-err.XXXXXX)"
  TEMP_PATHS+=("$out" "$err")
  "$@" >"$out" 2>"$err" & pid=$!; ACTIVE_PID="$pid"
  while kill -0 "$pid" 2>/dev/null; do
    [ "$INTERRUPTED" = "true" ] && break
    if [ "$SECONDS" -ge "$deadline" ]; then
      kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; ACTIVE_PID=""
      echo "WARN: $label exceeded the local deadline" >&2
      [ ! -s "$err" ] || cat "$err" >&2
      return 124
    fi
    sleep 1 || true
  done
  if wait "$pid"; then rc=0; else rc=$?; fi
  ACTIVE_PID=""
  printf -v "$var" '%s' "$(cat "$out")"
  [ "$rc" -eq 0 ] || { echo "WARN: $label failed (exit $rc)" >&2; [ ! -s "$err" ] || cat "$err" >&2; }
  return "$rc"
}

retry_readonly() {
  # Retry a read-only capture within a deadline. require=1 also rejects exit-0
  # empty output as transient. Stops at once if a post-dispatch signal is set.
  local require="$1" var="$2" deadline="$3" label="$4"; shift 4
  local n=0
  while [ "$SECONDS" -lt "$deadline" ]; do
    [ "$INTERRUPTED" = "true" ] && return 1
    n=$((n + 1))
    if capture_until_deadline "$var" "$deadline" "$label" "$@"; then
      [ "$require" -eq 0 ] && return 0
      [ -n "${!var:-}" ] && return 0
      echo "WARN: $label returned empty required output (attempt $n); retrying" >&2
    else
      echo "WARN: transient $label failure (attempt $n); retrying" >&2
    fi
    sleep_within_deadline "$deadline" "$POLL_INTERVAL_SECONDS" || break
  done
  return 1
}

fetch_run_state_once() {  # Read status|conclusion|url once; reject malformed output.
  local deadline="$1" line rest
  capture_until_deadline line "$deadline" "gh run view status" \
    gh run view "$RUN_ID" -R "$TESTING_REPO" --json status,conclusion,url \
      --jq '"\(.status)|\(.conclusion // "")|\(.url)"' || return 1
  [[ "$line" == *"|"*"|"* ]] || { echo "WARN: malformed run state; retrying" >&2; return 1; }
  STATUS="${line%%|*}"; rest="${line#*|}"; CONCLUSION="${rest%%|*}"; RUN_URL="${rest#*|}"
  { [ -n "$STATUS" ] && [[ "$RUN_URL" == https://* ]]; } || { echo "WARN: incomplete run state; retrying" >&2; return 1; }
  if [ "$STATUS" = "completed" ] && [ -z "$CONCLUSION" ]; then
    echo "WARN: completed without a conclusion; retrying" >&2; return 1
  fi
  return 0
}

poll_run_until_deadline() {  # Bounded read-only polling until completion or deadline.
  local deadline="$1"
  while [ "$SECONDS" -lt "$deadline" ]; do
    [ "$INTERRUPTED" = "true" ] && return 124
    if fetch_run_state_once "$deadline"; then
      echo "Run status: $STATUS${CONCLUSION:+ (conclusion: $CONCLUSION)}"
      [ "$STATUS" = "completed" ] && return 0
    else
      echo "WARN: transient run-status failure; retrying" >&2
    fi
    sleep_within_deadline "$deadline" "$POLL_INTERVAL_SECONDS" || break
  done
  return 124
}

# ===== STEP 1: validate local parameter forms =====
is_positive_integer "$POLL_INTERVAL_SECONDS"   || { echo "ERROR: poll_interval_seconds must be a positive integer"; exit 1; }
is_positive_integer "$MONITOR_TIMEOUT_SECONDS" || { echo "ERROR: monitor_timeout_seconds must be a positive integer"; exit 1; }
case "$MONITOR_MODE"  in watch|poll) ;; *) echo "ERROR: monitor_mode must be watch or poll"; exit 1 ;; esac
case "$DISPATCH_MODE" in standard|deterministic) ;; *) echo "ERROR: dispatch_mode must be standard or deterministic"; exit 1 ;; esac
[[ "$TESTING_REPO" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || { echo "ERROR: testing_repo must be owner/repo"; exit 1; }
{ [ -n "$LITE_REPO_REF" ] && [[ "$LITE_REPO_REF" != *$'\n'* ]]; } || { echo "ERROR: lite_repo_ref must be non-empty and single-line"; exit 1; }
{ [ -n "$TESTING_REF" ]   && [[ "$TESTING_REF"   != *$'\n'* ]]; } || { echo "ERROR: testing_ref must be non-empty and single-line"; exit 1; }
# expected_lite_sha is optional; when present it MUST be a 40-hex SHA (matched in Step 3).
[ -z "$EXPECTED_LITE_SHA" ] || [[ "$EXPECTED_LITE_SHA" =~ ^[0-9a-fA-F]{40}$ ]] \
  || { echo "ERROR: expected_lite_sha must be empty or a 40-hex SHA"; exit 1; }
LITE_REF_URL_PATH="$(urlencode_ref_path "$LITE_REPO_REF")"
[ -n "$LITE_REF_URL_PATH" ] || { echo "ERROR: lite_repo_ref could not be encoded for the branch link"; exit 1; }
printf 'Resolved: lite=%s@%s testing=%s@%s dispatch=%s monitor=%s timeout=%ss%s\n' \
  "$LITE_REPO" "$LITE_REPO_REF" "$TESTING_REPO" "$TESTING_REF" "$DISPATCH_MODE" \
  "$MONITOR_MODE" "$MONITOR_TIMEOUT_SECONDS" "${EXPECTED_LITE_SHA:+ expected_sha=$EXPECTED_LITE_SHA}"

# ===== STEP 2: preflight gh, auth, identity, permission, workflow access =====
command -v gh >/dev/null 2>&1 || { echo "ERROR: gh CLI not found on PATH"; exit 1; }
GH_READY=true
PRE=$((SECONDS + 90))
AUTH_STATUS=""
retry_readonly 0 AUTH_STATUS "$PRE" "gh auth status" gh auth status \
  || { echo "ERROR: gh authentication could not be verified"; exit 1; }
AUTH_OK=true
retry_readonly 1 ACTOR "$PRE" "identity lookup" gh api user --jq '.login' \
  || { echo "ERROR: authenticated GitHub identity could not be resolved"; exit 1; }
{ [ -n "$ACTOR" ] && [ "$ACTOR" != "null" ]; } || { echo "ERROR: authenticated identity was empty"; exit 1; }
echo "Authenticated as: $ACTOR"
CAN_PUSH=""
retry_readonly 1 CAN_PUSH "$PRE" "permission lookup" gh api "repos/$TESTING_REPO" --jq '.permissions.push' \
  || { echo "ERROR: repository permissions could not be verified"; exit 1; }
[ "$CAN_PUSH" = "true" ] || { echo "ERROR: $ACTOR lacks write (push) access to $TESTING_REPO; cannot dispatch"; exit 1; }
PERMISSION_OK=true
WF_VIEW=""
retry_readonly 0 WF_VIEW "$PRE" "workflow access" gh workflow view "$WORKFLOW_FILE" -R "$TESTING_REPO" \
  || { echo "ERROR: workflow $WORKFLOW_FILE not accessible on $TESTING_REPO"; exit 1; }
WORKFLOW_OK=true

# ===== STEP 3: resolve the upstream branch SHA; enforce expected_lite_sha if given =====
require_true GH_READY; require_true AUTH_OK
LITE_OWNER="${LITE_REPO%%/*}"; LITE_NAME="${LITE_REPO##*/}"
BR=$((SECONDS + 90))
retry_readonly 1 LITE_SHA "$BR" "upstream branch lookup" \
  gh api graphql -f owner="$LITE_OWNER" -f name="$LITE_NAME" \
    -f qualifiedName="refs/heads/$LITE_REPO_REF" \
    -f query='query($owner:String!,$name:String!,$qualifiedName:String!){repository(owner:$owner,name:$name){ref(qualifiedName:$qualifiedName){target{oid}}}}' \
    --jq '.data.repository.ref.target.oid' \
  || { echo "ERROR: upstream ref lookup failed"; exit 1; }
[[ "$LITE_SHA" =~ ^[0-9a-fA-F]{40}$ ]] \
  || { echo "ERROR: ref '$LITE_REPO_REF' not found in $LITE_REPO (fork-only branches are unsupported)"; exit 1; }
# Optional pin: the branch MUST resolve to exactly the caller-supplied commit
# (case-insensitive). tr runs on already-validated 40-hex text, never on a param.
if [ -n "$EXPECTED_LITE_SHA" ]; then
  if [ "$(printf '%s' "$LITE_SHA" | tr 'A-F' 'a-f')" != "$(printf '%s' "$EXPECTED_LITE_SHA" | tr 'A-F' 'a-f')" ]; then
    echo "ERROR: $LITE_REPO@$LITE_REPO_REF resolved to $LITE_SHA but expected_lite_sha=$EXPECTED_LITE_SHA"; exit 1
  fi
  echo "Upstream SHA matches expected_lite_sha"
fi
UPSTREAM_REF_OK=true
echo "Resolved $LITE_REPO@$LITE_REPO_REF -> $LITE_SHA"

# ===== STEP 4: verify the live workflow_dispatch inputs =====
require_true GH_READY; require_true AUTH_OK; require_true WORKFLOW_OK
CT=$((SECONDS + 90))
WF_YAML=""
retry_readonly 1 WF_YAML "$CT" "workflow contract fetch" \
  gh api --method GET "repos/$TESTING_REPO/contents/.github/workflows/$WORKFLOW_FILE" \
    -f ref="$TESTING_REF" -H "Accept: application/vnd.github.raw" \
  || { echo "ERROR: live workflow contract could not be fetched"; exit 1; }
# Scope both required keys to on.workflow_dispatch.inputs by indentation depth.
# [[:space:]] keeps awk portable; matching the names elsewhere would not prove
# the dispatch contract accepts them.
awk '
function lead(s,  t) { t = s; sub(/[^ ].*$/, "", t); return length(t) }
{ if ($0 ~ /^[[:space:]]*($|#)/) next; ind = lead($0)
  if (!on)  { if ($0 ~ /^on:[[:space:]]*($|#)/) { on = 1; oni = ind } next }
  if (ind <= oni) { on = disp = inp = 0; next }
  if (!disp) { if (onc < 0) onc = ind; if (ind == onc && $0 ~ /^[[:space:]]*workflow_dispatch:[[:space:]]*($|#)/) { disp = 1; di = ind } next }
  if (ind <= di)  { disp = inp = 0; next }
  if (!inp) { if (dc < 0) dc = ind; if (ind == dc && $0 ~ /^[[:space:]]*inputs:[[:space:]]*($|#)/) { inp = 1; ii = ind } next }
  if (ind <= ii)  { inp = 0; next }
  if (ik < 0) ik = ind; if (ind != ik) next
  if ($0 ~ /^[[:space:]]*lite_repo:[[:space:]]*($|#)/)     a = 1
  if ($0 ~ /^[[:space:]]*lite_repo_ref:[[:space:]]*($|#)/) b = 1 }
END { exit !(a && b) }' <<<"$WF_YAML" \
  || { echo "ERROR: inputs 'lite_repo' and 'lite_repo_ref' were not both found under on.workflow_dispatch.inputs"; exit 1; }
CONTRACT_OK=true
echo "Workflow contract verified: lite_repo and lite_repo_ref present"

# ===== STEP 5: snapshot the newest existing dispatch run id =====
require_true GH_READY; require_true AUTH_OK; require_true WORKFLOW_OK
SN=$((SECONDS + 90))
while [ "$SECONDS" -lt "$SN" ]; do
  PRE_RUN_ID=""
  if capture_until_deadline PRE_RUN_ID "$SN" "pre-dispatch snapshot" \
    gh run list -R "$TESTING_REPO" --workflow "$WORKFLOW_FILE" --event workflow_dispatch \
      --limit 1 --json databaseId --jq '.[0].databaseId // 0'; then
    [[ "$PRE_RUN_ID" =~ ^[0-9]+$ ]] && { SNAPSHOT_OK=true; break; }
    echo "WARN: snapshot returned a non-numeric id; retrying" >&2
  else
    echo "WARN: transient snapshot failure; retrying" >&2
  fi
  sleep_within_deadline "$SN" "$POLL_INTERVAL_SECONDS" || break
done
require_true SNAPSHOT_OK
echo "Newest pre-dispatch run id: $PRE_RUN_ID"

# ===== STEP 6: execute EXACTLY ONE dispatch (the one-shot boundary) =====
# These expansions intentionally fail in a fresh shell before any gh mutation.
: "${LITE_REPO:?}" "${LITE_REPO_REF:?}" "${TESTING_REPO:?}" "${TESTING_REF:?}"
: "${WORKFLOW_FILE:?}" "${DISPATCH_MODE:?}" "${PRE_RUN_ID:?}"
require_true GH_READY; require_true AUTH_OK; require_true PERMISSION_OK
require_true WORKFLOW_OK; require_true UPSTREAM_REF_OK; require_true CONTRACT_OK; require_true SNAPSHOT_OK
{ [ "$DISPATCH_ATTEMPTED" = "false" ] && [ "$DISPATCH_EXECUTED" = "false" ]; } \
  || { echo "ERROR: a dispatch was already attempted; refusing a duplicate"; exit 1; }

# Build the mode-specific command as an argv ARRAY so resolved values pass as
# separate arguments — never flattened into a string, eval'd, or sourced. Only
# -F return_run_details=true is a JSON boolean; refs/inputs stay raw -f strings.
DISPATCH_ARGV=()
case "$DISPATCH_MODE" in
  standard)
    DISPATCH_ARGV=(gh workflow run "$WORKFLOW_FILE" -R "$TESTING_REPO" --ref "$TESTING_REF"
      -f "lite_repo=$LITE_REPO" -f "lite_repo_ref=$LITE_REPO_REF") ;;
  deterministic)
    DISPATCH_ARGV=(gh api "repos/$TESTING_REPO/actions/workflows/$WORKFLOW_FILE/dispatches"
      -f "ref=$TESTING_REF" -f "inputs[lite_repo]=$LITE_REPO" -f "inputs[lite_repo_ref]=$LITE_REPO_REF"
      -F return_run_details=true --jq '[.workflow_run_id, .run_url, .html_url] | @tsv') ;;
esac
D_OUT="$(mktemp -t gg-uat-do.XXXXXX)"; D_ERR="$(mktemp -t gg-uat-de.XXXXXX)"
TEMP_PATHS+=("$D_OUT" "$D_ERR")
echo "Selected dispatch mode: $DISPATCH_MODE (exactly one dispatch will be attempted)"

# ---- One-shot boundary. Set the sole irreversible marker BEFORE the call, because
# gh may accept the request before we observe a result. From here: no exit before
# Step 10, no retry, no mode switch. A signal during the call is handled as
# post-dispatch (uncertain) — the safe, conservative direction. ----
DISPATCH_ATTEMPTED=true
if "${DISPATCH_ARGV[@]}" >"$D_OUT" 2>"$D_ERR"; then DISPATCH_RC=0; else DISPATCH_RC=$?; fi

if [ "$INTERRUPTED" = "true" ]; then
  : # A signal was recorded during dispatch; its outcome is authoritative. Drain to Step 7+.
else
  case "$DISPATCH_MODE" in
    standard)
      if [ "$DISPATCH_RC" -eq 0 ]; then
        DISPATCH_EXECUTED=true
        [ ! -s "$D_OUT" ] || cat "$D_OUT"   # a successful standard dispatch may print nothing
      else
        [ ! -s "$D_ERR" ] || cat "$D_ERR" >&2
        enter_finalization_only "dispatch-command-failed" \
          "mode=standard; gh exit=$DISPATCH_RC; acceptance unknown; inspect the workflow page; do not retry"
        echo "ERROR: standard dispatch errored; it may already have been accepted. Not retrying or switching modes."
      fi ;;
    deterministic)
      if [ "$DISPATCH_RC" -eq 0 ] && [ -s "$D_OUT" ]; then
        DISPATCH_EXECUTED=true
        # Consume the prepared TSV with a built-in read: no eval, no command substitution.
        IFS=$'\t' read -r RUN_ID RUN_API_URL RUN_URL < "$D_OUT" || true
        if [[ ! "$RUN_ID" =~ ^[0-9]+$ ]] || [[ "$RUN_API_URL" != https://* ]] || [[ "$RUN_URL" != https://* ]]; then
          RUN_ID=""; RUN_API_URL=""; RUN_URL=""   # partial details are not canonical correlation
          enter_finalization_only "dispatch-response-invalid" \
            "mode=deterministic; success but workflow_run_id/run_url/html_url missing or malformed; inspect the workflow page; do not retry"
          echo "ERROR: deterministic response lacked valid run details. Not retrying or switching modes."
        fi
      else
        [ ! -s "$D_ERR" ] || cat "$D_ERR" >&2
        enter_finalization_only "dispatch-command-failed" \
          "mode=deterministic; gh exit=$DISPATCH_RC; acceptance or parse unknown; inspect the workflow page; do not retry"
        echo "ERROR: deterministic dispatch or parse failed. Not retrying or falling back to standard."
      fi ;;
  esac
fi
if [ "$INTERRUPTED" = "false" ] && [ "$FINALIZE_ONLY" = "false" ]; then
  echo "Dispatched $WORKFLOW_FILE on $TESTING_REPO@$TESTING_REF using $DISPATCH_MODE mode"
fi

# ===== STEP 7: correlate the standard-mode run id (bounded) =====
require_true DISPATCH_ATTEMPTED
if [ "$FINALIZE_ONLY" = "true" ]; then
  echo "Correlation skipped: finalization-only reporting is required"
elif [ "$DISPATCH_EXECUTED" != "true" ]; then
  enter_finalization_only "dispatch-state-unknown" \
    "dispatch attempted but execution not confirmed; inspect the workflow page; do not retry"
  echo "ERROR: dispatch execution state is unknown; skipping correlation"
elif [ "$DISPATCH_MODE" = "standard" ]; then
  DISCOVERY_TIMEOUT_SECONDS=120; DD=$((SECONDS + DISCOVERY_TIMEOUT_SECONDS)); RUN_ID=""
  while [ "$SECONDS" -lt "$DD" ]; do
    [ "$INTERRUPTED" = "true" ] && break
    CAND=""
    if capture_until_deadline CAND "$DD" "run discovery" \
      gh run list -R "$TESTING_REPO" --workflow "$WORKFLOW_FILE" --event workflow_dispatch \
        --user "$ACTOR" --branch "$TESTING_REF" --limit 1 --json databaseId --jq '.[0].databaseId // 0'; then
      if [[ "$CAND" =~ ^[0-9]+$ ]]; then
        [ "$CAND" -gt "$PRE_RUN_ID" ] && { RUN_ID="$CAND"; break; }   # a newer id ⇒ this dispatch
      else
        echo "WARN: discovery returned a non-numeric id; retrying" >&2
      fi
    else
      echo "WARN: transient discovery failure; retrying" >&2
    fi
    sleep_within_deadline "$DD" "$POLL_INTERVAL_SECONDS" || break
  done
  if [ "$INTERRUPTED" = "true" ]; then
    echo "Correlation interrupted by SIG${SIGNAL_NAME}; continuing to the final summary"
  elif [[ ! "$RUN_ID" =~ ^[0-9]+$ ]]; then
    printf -v DISCOVERY_DIAGNOSTICS 'actor=%s; pre-run-id=%s; timeout=%ss; inspect: gh run list -R %s --workflow %s --event workflow_dispatch' \
      "$ACTOR" "$PRE_RUN_ID" "$DISCOVERY_TIMEOUT_SECONDS" "$TESTING_REPO" "$WORKFLOW_FILE"
    enter_finalization_only "discovery-failed" "$DISCOVERY_DIAGNOSTICS"
    echo "ERROR: no new $WORKFLOW_FILE run correlated within ${DISCOVERY_TIMEOUT_SECONDS}s; not inventing a run URL"
  else
    echo "Correlated run id: $RUN_ID"
  fi
else
  if [[ "$RUN_ID" =~ ^[0-9]+$ ]]; then
    echo "Captured deterministic run id: $RUN_ID"
  else
    RUN_ID=""; RUN_URL=""; RUN_API_URL=""
    enter_finalization_only "dispatch-response-invalid" \
      "mode=deterministic; no numeric run id remained after the attempt; inspect the workflow page; do not retry"
    echo "ERROR: deterministic mode did not retain a numeric run id"
  fi
fi

# ===== STEP 8: print labeled links; resolve the canonical run URL when trustworthy =====
require_true DISPATCH_ATTEMPTED
: "${LITE_SHA:?}" "${LITE_REF_URL_PATH:?}"
if [ "$FINALIZE_ONLY" = "true" ]; then
  echo "Run URL          : unavailable (no canonical run URL)"
elif [[ ! "$RUN_ID" =~ ^[0-9]+$ ]]; then
  RUN_ID=""; RUN_URL=""
  enter_finalization_only "run-id-unavailable" \
    "attempted but no canonical numeric run id; inspect the workflow page; do not retry"
  echo "Run URL          : unavailable (no canonical run ID)"
else
  if [ -z "$RUN_URL" ]; then
    LK=$((SECONDS + 90))
    if ! retry_readonly 1 RUN_URL "$LK" "run URL lookup" \
      gh run view "$RUN_ID" -R "$TESTING_REPO" --json url --jq '.url'; then
      # A recorded signal is not a URL-lookup failure; leave its state intact.
      if [ "$INTERRUPTED" = "false" ]; then
        RUN_URL=""
        enter_finalization_only "run-url-lookup-failed" \
          "run id=$RUN_ID; canonical URL lookup exhausted its 90s deadline; inspect the workflow page; do not redispatch"
      fi
    fi
  fi
  if [ "$INTERRUPTED" = "false" ] && [ "$FINALIZE_ONLY" = "false" ] && [[ "$RUN_URL" != https://* ]]; then
    RUN_URL=""
    enter_finalization_only "run-url-lookup-failed" \
      "run id=$RUN_ID; canonical URL was empty or malformed; inspect the workflow page; do not redispatch"
  fi
  if [ "$FINALIZE_ONLY" = "true" ]; then
    echo "Run URL          : unavailable (no canonical run URL verified for run id $RUN_ID)"
  else
    echo "Run URL          : $RUN_URL"
  fi
fi
echo "Workflow page    : https://github.com/$TESTING_REPO/actions/workflows/$WORKFLOW_FILE"
echo "Lite branch      : https://github.com/$LITE_REPO/tree/$LITE_REF_URL_PATH"
echo "Lite commit      : https://github.com/$LITE_REPO/commit/$LITE_SHA"

# ===== STEP 9: monitor within the local deadline (timeout stops LOCAL only) =====
require_true DISPATCH_ATTEMPTED
if [ "$FINALIZE_ONLY" = "true" ]; then
  echo "Monitoring skipped: finalization-only reporting is required"
else
  : "${RUN_ID:?}" "${RUN_URL:?}" "${MONITOR_TIMEOUT_SECONDS:?}"
  MD=$((SECONDS + MONITOR_TIMEOUT_SECONDS)); MONITOR_TIMED_OUT=false; WATCH_RC=0
  if [ "$MONITOR_MODE" = "watch" ]; then
    # Background watch gives a portable deadline without GNU timeout (macOS lacks it).
    gh run watch "$RUN_ID" -R "$TESTING_REPO" --exit-status --interval "$POLL_INTERVAL_SECONDS" &
    WATCH_PID=$!; ACTIVE_PID="$WATCH_PID"
    while kill -0 "$WATCH_PID" 2>/dev/null; do
      [ "$INTERRUPTED" = "true" ] && break
      if [ "$SECONDS" -ge "$MD" ]; then
        kill "$WATCH_PID" 2>/dev/null || true       # stop local streaming only; never gh run cancel
        if wait "$WATCH_PID"; then WATCH_RC=0; else WATCH_RC=$?; fi
        ACTIVE_PID=""; MONITOR_TIMED_OUT=true; break
      fi
      sleep 1 || true
    done
    if [ "$INTERRUPTED" = "false" ] && [ "$MONITOR_TIMED_OUT" = "false" ]; then
      if wait "$WATCH_PID"; then WATCH_RC=0; else WATCH_RC=$?; fi
      ACTIVE_PID=""
      # A nonzero watch, or any non-completed/malformed confirmation, falls back
      # to bounded read-only polling — never a new run.
      if [ "$WATCH_RC" -ne 0 ]; then
        echo "WARN: gh run watch exited $WATCH_RC; checking whether the run is still active"
        if fetch_run_state_once "$MD" && [ "$STATUS" = "completed" ]; then
          echo "Watch ended nonzero because the run completed (conclusion: ${CONCLUSION:-unknown})"
        else
          echo "Falling back to bounded status polling for the remaining deadline"
          poll_run_until_deadline "$MD" || MONITOR_TIMED_OUT=true
        fi
      elif ! { fetch_run_state_once "$MD" && [ "$STATUS" = "completed" ]; }; then
        echo "WARN: final state unavailable, malformed, or not completed; falling back to bounded polling"
        poll_run_until_deadline "$MD" || MONITOR_TIMED_OUT=true
      fi
    fi
  else
    poll_run_until_deadline "$MD" || MONITOR_TIMED_OUT=true
  fi

  if [ "$INTERRUPTED" = "true" ]; then
    # A signal was recorded during monitoring; leave its outcome/exit for Step 10.
    echo "Monitoring interrupted by SIG${SIGNAL_NAME}; continuing to the final summary"
  elif [ "$MONITOR_TIMED_OUT" = "true" ]; then
    FR=$((SECONDS + 1)); fetch_run_state_once "$FR" || true   # best-effort final read for reporting
    STATUS="${STATUS:-unknown}"; OUTCOME="monitor-timeout"; RESULT_EXIT=1
    echo "MONITOR TIMEOUT: local monitoring stopped after ${MONITOR_TIMEOUT_SECONDS}s"
    echo "GitHub run was NOT cancelled and may still be active"
    echo "Run status: $STATUS"; echo "Run URL: $RUN_URL"
  elif [ "$STATUS" != "completed" ]; then
    OUTCOME="monitor-error"; RESULT_EXIT=1
    echo "ERROR: monitoring ended without a completed run state (status: ${STATUS:-unknown})"
  elif [ "$CONCLUSION" = "success" ]; then
    OUTCOME="success"; RESULT_EXIT=0
  else
    OUTCOME="run-${CONCLUSION:-unknown}"; RESULT_EXIT=1
  fi
  [ "$INTERRUPTED" = "true" ] || echo "Conclusion: ${CONCLUSION:-unknown}"
fi

# ===== STEP 10: failure logs + the MANDATORY durable final summary =====
require_true DISPATCH_ATTEMPTED
if [ "$INTERRUPTED" = "true" ]; then
  echo "Post-dispatch processing was interrupted by SIG${SIGNAL_NAME}"
  echo "Outcome: $OUTCOME"; echo "Diagnostics: ${FINALIZATION_DIAGNOSTICS:-none available}"
  echo "The GitHub run was NOT cancelled by this signal and may still be active"
elif [ "$FINALIZE_ONLY" = "true" ]; then
  echo "Post-dispatch processing ended in finalization-only mode"
  echo "Outcome: $OUTCOME"; echo "Diagnostics: ${FINALIZATION_DIAGNOSTICS:-none available}"
elif [ "$STATUS" = "completed" ] && [ "$CONCLUSION" != "success" ]; then
  echo "UAT did not succeed (conclusion: ${CONCLUSION:-unknown})"
  FAILED_LOGS=""; LG=$((SECONDS + 120))
  if capture_until_deadline FAILED_LOGS "$LG" "failed-log fetch" \
    gh run view "$RUN_ID" -R "$TESTING_REPO" --log-failed; then
    printf '%s\n' "$FAILED_LOGS"
  else
    echo "WARN: failed-step logs could not be retrieved within 120s"   # a log-fetch miss must not hide the summary
  fi
  echo "Failed run: $RUN_URL"
elif [ "$OUTCOME" = "monitor-timeout" ]; then
  echo "Local monitor timed out; the GitHub run was left unchanged: $RUN_URL"
fi

# Never promote partial or malformed values into the durable record.
if [[ "$RUN_ID"  =~ ^[0-9]+$ ]]; then RUN_ID_SUMMARY="$RUN_ID"; else RUN_ID_SUMMARY="NOT AVAILABLE (no canonical run ID was verified)"; fi
if [[ "$RUN_URL" == https://* ]]; then RUN_URL_SUMMARY="$RUN_URL"; else RUN_URL_SUMMARY="NOT AVAILABLE (no canonical run URL was verified)"; fi
DIAG_SUMMARY="${FINALIZATION_DIAGNOSTICS:-${DISCOVERY_DIAGNOSTICS:-none}}"
# A dispatch attempt that never reached a terminal outcome is itself a failure.
if [ "$OUTCOME" = "not-started" ]; then
  OUTCOME="post-dispatch-state-unknown"; RESULT_EXIT=1
  DIAG_SUMMARY="${DIAG_SUMMARY}; dispatch was attempted but no terminal outcome was recorded"
fi
# Preserve a signal-specific 130/143 exit; other finalization paths return 1.
if [ "$FINALIZE_ONLY" = "true" ] && [ "$INTERRUPTED" = "false" ]; then RESULT_EXIT=1; fi

cat <<EOF
==================== UAT RESULT ====================
Lite repo   : $LITE_REPO
Lite ref    : $LITE_REPO_REF
Lite commit : $LITE_SHA
Testing repo: $TESTING_REPO @ $TESTING_REF
Dispatch    : $DISPATCH_MODE (attempted: $DISPATCH_ATTEMPTED; execution confirmed: $DISPATCH_EXECUTED)
Interrupted : $INTERRUPTED${SIGNAL_NAME:+ (SIG$SIGNAL_NAME)}
Run ID      : $RUN_ID_SUMMARY
Status      : ${STATUS:-unknown}
Conclusion  : ${CONCLUSION:-unknown}
Outcome     : $OUTCOME
Run URL     : $RUN_URL_SUMMARY
Diagnostics : $DIAG_SUMMARY
Workflow    : https://github.com/$TESTING_REPO/actions/workflows/$WORKFLOW_FILE
Lite branch : https://github.com/$LITE_REPO/tree/$LITE_REF_URL_PATH
Lite commit : https://github.com/$LITE_REPO/commit/$LITE_SHA
====================================================
EOF
exit "$RESULT_EXIT"
```

## Steps

Each step below is one labeled section of the single transaction above. You MUST
run them in order as one script; do not reorder, split, or re-run any of them.

### 1. Bind arguments and validate local parameter forms

Bind the eight positional arguments, validate numbers, enums, the `owner/repo`
slug, single-line refs, and the optional 40-hex `expected_lite_sha`, and encode
the ref for the branch link.

**Constraints:**

- You MUST bind exactly eight positional values in the documented order and MUST
  NOT substitute raw values into the source, because generated shell text could
  reinterpret user-controlled characters
- You MUST keep `LITE_REPO` and `WORKFLOW_FILE` fixed, because fork repositories
  and other workflows are out of scope
- You MUST validate both numeric parameters as positive integers before any
  GitHub call, because deadlines and arithmetic require numbers
- You MUST NOT add a token variable or enable shell tracing (`set -x`), because
  either could expose authentication material
- You MUST install the INT/TERM trap here so it governs the whole transaction

### 2. Preflight gh, authentication, identity, and permissions

Verify `gh`, authentication, the actor identity, write (push) access to
`testing_repo`, and workflow accessibility; each guard turns true only on
success.

**Constraints:**

- You MUST complete every check and set its guard only on success, because Step
  6 requires all guards true
- You MUST hard-stop on any auth, identity, permission, or workflow-access
  failure, because dispatch is unsafe when preflight state is unknown
- You MUST NOT print or persist the authentication token, because that could
  expose credentials
- You SHOULD surface the exact failed check, because 403 and 404 have different
  remediations

### 3. Resolve the upstream branch SHA

Resolve the ref against the fixed upstream lite repo via GraphQL
`qualifiedName`; reject non-SHA results; when `expected_lite_sha` is set,
require an exact match.

**Constraints:**

- You MUST resolve the ref only against `aws-greengrass/aws-greengrass-lite`,
  because fork-only branches cannot run when the workflow pins upstream
- You MUST reject empty, null, or non-SHA results and MUST NOT fall back to lite
  `main`, because that would test unintended code
- When `expected_lite_sha` is non-empty, You MUST require the branch to resolve
  to exactly that commit before dispatch, because the caller pinned a specific
  commit
- You MUST retain the SHA, because the commit link must pin the exact tested
  code

### 4. Verify the live workflow contract

Fetch `uat.yml` from `testing_ref` and confirm `lite_repo` and `lite_repo_ref`
both exist under `on.workflow_dispatch.inputs`.

**Constraints:**

- You MUST fetch with `--method GET -f ref=...`, because `gh` must URL-encode
  valid ref characters instead of treating them as query delimiters
- You MUST scope both keys to `on.workflow_dispatch.inputs`, because matching
  the names elsewhere does not prove the dispatch contract accepts them
- You MUST use `[[:space:]]` in awk, because `\s` is not portable
- You MUST hard-stop on contract drift, because Step 6 requires
  `CONTRACT_OK=true`

### 5. Snapshot the newest existing dispatch run id

Record and numerically validate the newest existing `workflow_dispatch` run id
(`0` if none) for standard-mode correlation.

**Constraints:**

- You MUST numerically validate the snapshot before dispatch, because
  standard-mode correlation accepts only a newer `databaseId`
- You MUST use `0` when no prior run exists, because the first dispatch has no
  predecessor
- You MUST bound retries within the deadline and MUST NOT rely on timestamps
  alone, because runs can share a start second

### 6. Execute exactly one dispatch

Reassert every guard, build the mode-specific argv array, then cross the
one-shot boundary: set `DISPATCH_ATTEMPTED=true` and run the single dispatch,
classifying the result without retry or fallback.

**Constraints:**

- You MUST keep `standard` as the default and primary path and execute exactly
  one case, because running both creates duplicate CI work
- You MUST reassert every guard here, because a stray fresh-shell invocation
  must make dispatch mechanically impossible
- You MUST set `DISPATCH_ATTEMPTED=true` before the command and MUST NOT retry,
  switch modes, or redispatch after any error or signal, because GitHub may have
  accepted the request before the client observed a failure
- You MUST build the command as an argv array passed directly, and MUST NOT
  flatten, re-quote, `eval`, `source`, or command-substitute the dispatch
  arguments, because shell re-evaluation could reinterpret user-controlled
  ref/input text
- You MUST convert any post-attempt command or response failure into
  `FINALIZE_ONLY=true` and continue to Step 10, and MUST NOT let a failure
  branch overwrite a recorded signal outcome, because both must survive to the
  durable summary
- You MUST clear malformed deterministic run details, because partial values
  must not be presented as a canonical run ID or URL
- You MUST NOT use `curl`, `git`, or a browser to dispatch, because this SOP
  uses `gh` exclusively

### 7. Correlate the standard-mode run id

In standard mode, poll for a run id greater than the snapshot within a bounded
120s deadline; deterministic mode already holds the id. Skip when finalizing.

**Constraints:**

- You MUST require only `DISPATCH_ATTEMPTED` at entry, because Step 6 may leave
  `DISPATCH_EXECUTED` false or uncertain
- You MUST skip correlation when `FINALIZE_ONLY=true`, because further lookup
  cannot prove whether the uncertain request was accepted
- You MUST accept only a numeric id greater than the snapshot, filtered by
  workflow, event, actor, and testing branch, because that reliably isolates
  this dispatch
- You MUST NOT dispatch again when correlation fails, because a second run
  worsens ambiguity and consumes shared CI
- If an INT/TERM signal is recorded mid-correlation, You MUST break out without
  a discovery-failed classification, because the signal outcome must not be
  overwritten

### 8. Print available labeled links

Resolve the canonical run URL only when a trustworthy run id exists and no
finalization is active; always print the static workflow, branch, and commit
links.

**Constraints:**

- You MUST use the canonical `gh run view --json url` value or the deterministic
  `html_url` for the run link, and MUST NOT fabricate a URL, because an invented
  resource misleads investigation
- You MUST build the commit link from the Step 3 SHA and percent-encode the ref
  while preserving `/`, because that pins exact code and avoids broken links
- On any finalization-only path, You MUST print the workflow, branch, and commit
  links plus an explicit unavailable run-URL placeholder, because the user still
  needs to investigate
- If a canonical URL lookup exhausts its deadline or returns malformed data, You
  MUST record `run-url-lookup-failed`, skip monitoring, and continue to Step 10,
  because the link was not verified
- If a signal is recorded during the lookup, You MUST leave the signal state
  intact, because it is not a URL-lookup failure

### 9. Monitor within the local deadline

Use one deadline for watch and poll. In watch mode, run `gh run watch` as a
background process, fall back to bounded polling on a nonzero/non-completed
result, and stop only the local process at the deadline. Skip when finalizing.

**Constraints:**

- You MUST skip all monitor calls when `FINALIZE_ONLY=true`, because an
  uncertain mutation result must flow straight to Step 10
- You MUST enforce `monitor_timeout_seconds` for both paths using the
  background-process/deadline pattern, because neither may run forever and macOS
  lacks GNU `timeout`
- You MUST use `WATCH_RC` to distinguish a completed run from a stream/auth
  failure and fall back to polling, and MUST retry transient or malformed states
  within the remaining deadline
- On timeout, You MUST stop only the local monitor, print status and URL, report
  `monitor-timeout`, and exit nonzero; You MUST NOT call `gh run cancel`,
  because a local timeout does not authorize cancelling the remote run
- If a signal is recorded during monitoring, You MUST break out and leave the
  signal outcome and exit status for Step 10, because normal classification
  would overwrite them

### 10. Print failure logs and the final summary

For a completed non-successful run, make one bounded attempt to print
failed-step logs, then always print the durable summary and exit with the
computed status.

**Constraints:**

- You MUST require only `DISPATCH_ATTEMPTED`, because command or response
  failure can leave `DISPATCH_EXECUTED` false after GitHub may have accepted the
  request
- You MUST treat every non-`success` conclusion, plus
  dispatch/response/discovery/URL failure, monitor timeout, monitor error, and
  unknown post-dispatch state, as failure, because partial or unconfirmed UAT
  does not validate the branch
- You MUST print the final summary after every dispatch attempt with explicit
  unavailable placeholders for any unverified run ID or URL, and MUST NOT invent
  either, because it is the durable correlation and diagnostic record
- You MUST NOT automatically cancel, rerun, or delete the run, because those
  consume resources or hide signal and need a separate user decision
- When an INT/TERM signal was recorded after dispatch, You MUST report the
  signal state, make no further run-specific `gh` calls, and exit with the
  recorded 130/143 status, because that status must reach the caller intact

## Examples

### Example 1: Standard dispatch with watch monitoring

**Input:** `lite_repo_ref: dev/ipc-connection-status` (all other parameters
default). **Expected behavior:** The caller resolves defaults and passes eight
positional arguments. The transaction preflights, resolves the upstream SHA,
verifies the workflow inputs, snapshots the current run id, runs one
`gh workflow run`, correlates the newer run, prints four links, and watches for
up to three hours; a stream/auth failure or stale read falls back to bounded
polling.

### Example 2: Poll monitoring with a shorter deadline

**Input:** `lite_repo_ref: dev/ipc-connection-status`, `monitor_mode: poll`,
`monitor_timeout_seconds: 7200`. **Expected behavior:** Streaming is skipped for
bounded `gh run view` polling. Transient/malformed responses retry within the
two-hour deadline; expiry reports `monitor-timeout`, leaves the run active, and
exits nonzero.

### Example 3: Deterministic dispatch pinned to a commit

**Input:** `lite_repo_ref: dev/ipc-connection-status`,
`dispatch_mode: deterministic`, `expected_lite_sha: <40-hex>`. **Expected
behavior:** Step 3 requires the branch to resolve to exactly that SHA. The
transaction invokes only the REST dispatch with boolean
`return_run_details=true`, captures the returned run id and URL, and never calls
`gh workflow run`. Any command/response/parse problem enters finalization-only
mode without retry or fallback and prints the nonzero summary with static links.

## Troubleshooting

### Parameter binding or argument-count failure

The script requires exactly eight positional arguments. Resolve defaults before
invocation and use an argv-capable launcher; never write assignments into the
script or `eval`/`source` parameter text. Stop before dispatch if safe argument
transport is unavailable.

### expected_lite_sha mismatch

Step 3 aborts before dispatch when the branch does not resolve to the pinned
SHA. The branch advanced, or the SHA is stale. Re-fetch the intended commit or
clear `expected_lite_sha` for branch-only validation. Never dispatch on a
mismatch.

### 403 on dispatch or `permissions.push` is not `true`

The identity lacks write access or the token lacks scope. Confirm with
`gh api repos/aws-greengrass/aws-greengrass-testing --jq '.permissions.push'`. A
classic/OAuth token needs `repo`; a fine-grained PAT or App needs **Actions:
read and write**. The `workflow` scope alone does not grant dispatch.

### 404 on the workflow or repository, or changed inputs

The workflow is wrong/disabled/inaccessible, or `lite_repo`/`lite_repo_ref` are
absent from the live `uat.yml`. Verify with
`gh workflow view uat.yml -R aws-greengrass/aws-greengrass-testing` and inspect
the workflow source. Update this SOP only after confirming a new contract.

### Branch not found or fork-only branch

The GraphQL ref query returned null or a non-SHA; the branch may exist only in a
fork. Push it upstream or supply another upstream ref. Never substitute lite
`main`.

### Dispatch, response, or canonical-URL failure after the attempt

These are intentionally not retried and do not exit early. Steps 7–9 skip unsafe
follow-up, and Step 10 prints a nonzero finalization-only summary with
diagnostics, static links, and unavailable placeholders. Inspect Actions
manually; do not dispatch again or switch modes.

### No correlated run within 120 seconds

The dispatch may have been rejected or delayed, or a concurrent same-actor,
same-ref run made correlation ambiguous. Inspect with
`gh run list -R aws-greengrass/aws-greengrass-testing --workflow uat.yml --event workflow_dispatch`.
The SOP records `discovery-failed`, continues to Step 10, and leaves the run URL
unavailable rather than inventing one. Deterministic mode avoids this heuristic.

### `gh run watch` exits nonzero

Some fine-grained PATs cannot grant `checks:read`, and streaming can fail
transiently. Step 9 uses `WATCH_RC`, checks run state, and falls back to bounded
polling when the run is still active. Set `monitor_mode=poll` to skip streaming.

### Local monitor timeout

The deadline expired before completion was observed. The SOP kills only its
local `gh` process, prints the last status and URL, reports `monitor-timeout`,
and exits nonzero. The GitHub run remains active; open the URL. Do not read a
local timeout as GitHub cancellation.

### Interrupted by Ctrl-C or SIGTERM (INT/TERM)

Before the attempt, a signal stops any local process and exits 130 (INT) or 143
(TERM) with no summary, because nothing was mutated. After
`DISPATCH_ATTEMPTED=true`, the handler stops only the local monitor, records an
`interrupted-int`/`-term` outcome, and lets Steps 7–9 skip so Step 10 prints the
signal state and static links, then exits 130/143. The run is never cancelled by
the signal. Because the one-shot marker is set just before the call, a signal in
that brief window is reported as uncertain rather than as "nothing dispatched" —
the safe direction. A `kill -9` or lost machine bypasses the trap and cannot
guarantee a summary; inspect Actions manually in that case.

### Failed, cancelled, or GitHub-timed-out UAT

Any conclusion other than `success` is failure. Read failed steps with
`gh run view <run-id> -R aws-greengrass/aws-greengrass-testing --log-failed` and
open the run URL. Do not automatically rerun or cancel.

## Source Links

- Live workflow:
  https://github.com/aws-greengrass/aws-greengrass-testing/actions/workflows/uat.yml
- Workflow source:
  https://github.com/aws-greengrass/aws-greengrass-testing/blob/main/.github/workflows/uat.yml
- `gh workflow run`: https://cli.github.com/manual/gh_workflow_run
- `gh run watch`: https://cli.github.com/manual/gh_run_watch
- `gh run view`: https://cli.github.com/manual/gh_run_view
- Create a workflow dispatch event (REST):
  https://docs.github.com/en/rest/actions/workflows#create-a-workflow-dispatch-event
- Workflow dispatch run-details changelog (2026-02-19):
  https://github.blog/changelog/2026-02-19-workflow-dispatch-api-now-returns-run-ids/
