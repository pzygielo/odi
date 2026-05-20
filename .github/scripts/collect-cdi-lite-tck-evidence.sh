#!/usr/bin/env bash
set -euo pipefail

java_version="${1:?Usage: collect-cdi-lite-tck-evidence.sh <java-version>}"
repo_root="$(git rev-parse --show-toplevel)"
cd "${repo_root}"

evidence_dir="build/tck-evidence/jdk-${java_version}"
result_dir="tck-runner/build/test-results/fullTckTest"
summary_file="${evidence_dir}/summary.md"
index_file="${evidence_dir}/index.html"

rm -rf "${evidence_dir}"
mkdir -p "${evidence_dir}"

project_version="$(sed -n 's/^projectVersion=//p' gradle.properties | head -1)"
cdi_version="$(sed -n 's/^cdi = "\(.*\)"/\1/p' gradle/libs.versions.toml | head -1)"
spec_version="${cdi_version%.*}"
odi_commit="$(git rev-parse HEAD)"
micronaut_core_commit="unavailable"
if [ -d "checkouts/micronaut-core-cdi/.git" ]; then
  micronaut_core_commit="$(git -C checkouts/micronaut-core-cdi rev-parse HEAD)"
fi

tests="0"
failures="0"
errors="0"
skipped="0"
if [ -d "${result_dir}" ] && find "${result_dir}" -name 'TEST-*.xml' -print -quit | grep -q .; then
  totals="$(
    find "${result_dir}" -name 'TEST-*.xml' -print0 |
      xargs -0 awk '
        match($0, /tests="[0-9]+"/) { tests += substr($0, RSTART + 7, RLENGTH - 8) }
        match($0, /failures="[0-9]+"/) { failures += substr($0, RSTART + 10, RLENGTH - 11) }
        match($0, /errors="[0-9]+"/) { errors += substr($0, RSTART + 8, RLENGTH - 9) }
        match($0, /skipped="[0-9]+"/) { skipped += substr($0, RSTART + 9, RLENGTH - 10) }
        END { printf "%d %d %d %d", tests, failures, errors, skipped }
      '
  )"
  read -r tests failures errors skipped <<< "${totals}"
fi

java_runtime="$(java -version 2>&1)"
generated_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
workflow_url="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-eclipse-ee4j/odi}/actions/runs/${GITHUB_RUN_ID:-local}"

rm -rf "${evidence_dir}/junit-xml"
if [ -d "${result_dir}" ]; then
  mkdir -p "${evidence_dir}/junit-xml"
  for result_file in "${result_dir}"/TEST-*.xml; do
    [ -f "${result_file}" ] || continue
    perl -0pe 's#<system-out>.*?</system-out>\n?##gs; s#<system-err>.*?</system-err>\n?##gs' \
      "${result_file}" > "${evidence_dir}/junit-xml/$(basename "${result_file}")"
  done
fi

cat > "${summary_file}" <<EOF
# ODI CDI Lite TCK Results - JDK ${java_version}

Generated: ${generated_at}

## Scope

ODI is validating CDI Lite compatibility only. This evidence does not claim CDI Full compatibility.

Excluded TestNG groups: \`cdi-full\`, \`integration\`, \`javaee-full\`, \`se\`.

## Test Results

- Tests: ${tests}
- Failures: ${failures}
- Errors: ${errors}
- Skipped: ${skipped}

## Product

- Organization: Oracle
- Product: Open DI (ODI)
- Version: ${project_version}
- Repository: https://github.com/eclipse-ee4j/odi
- ODI commit: ${odi_commit}
- Included Micronaut Core commit: ${micronaut_core_commit}

## Specification And TCK

- Specification: Jakarta Contexts Dependency Injection ${spec_version}
- Specification URL: https://jakarta.ee/specifications/cdi/${spec_version}/
- TCK: Jakarta CDI TCK ${cdi_version}
- TCK download: https://download.eclipse.org/ee4j/cdi/${spec_version}/cdi-tck-${cdi_version}-dist.zip
- TCK SHA-256: 446029ee1ce694d2a9ae8893d16be7afd7e1c0ed8705064b7095af174cf97ea0

## Additional Certification Requirements

- Signature-test evidence: pending; this workflow does not claim signature tests passed.

## Environment

- Workflow run: ${workflow_url}
- Runner OS: ${RUNNER_OS:-local}
- Runner architecture: ${RUNNER_ARCH:-unknown}
- Java matrix version: ${java_version}

\`\`\`
${java_runtime}
\`\`\`

## Artifacts

- Sanitized JUnit XML: ./junit-xml/

Raw Gradle console output and unsanitized Gradle reports are intentionally not published because the build runs with secret-backed environment variables.
EOF

cat > "${index_file}" <<EOF
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ODI CDI Lite TCK Results - JDK ${java_version}</title>
  <style>
    body { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 1.5; max-width: 960px; margin: 2rem auto; padding: 0 1rem; color: #1f2933; }
    h1, h2 { line-height: 1.2; }
    code, pre { background: #f5f7fa; border-radius: 4px; }
    code { padding: 0.1rem 0.25rem; }
    pre { padding: 1rem; overflow-x: auto; }
    table { border-collapse: collapse; width: 100%; margin: 1rem 0; }
    th, td { border: 1px solid #d9e2ec; padding: 0.5rem; text-align: left; }
    th { background: #f5f7fa; }
    .notice { border-left: 4px solid #d64545; background: #fff5f5; padding: 0.75rem 1rem; }
  </style>
</head>
<body>
  <h1>ODI CDI Lite TCK Results - JDK ${java_version}</h1>
  <p>Generated: ${generated_at}</p>

  <div class="notice">
    <strong>Scope:</strong> ODI is validating CDI Lite compatibility only. This evidence does not claim CDI Full compatibility.
  </div>

  <h2>Test Results</h2>
  <table>
    <tr><th>Tests</th><th>Failures</th><th>Errors</th><th>Skipped</th></tr>
    <tr><td>${tests}</td><td>${failures}</td><td>${errors}</td><td>${skipped}</td></tr>
  </table>

  <h2>TCK Configuration</h2>
  <p>Excluded TestNG groups: <code>cdi-full</code>, <code>integration</code>, <code>javaee-full</code>, <code>se</code>.</p>

  <h2>Product</h2>
  <ul>
    <li>Organization: Oracle</li>
    <li>Product: Open DI (ODI)</li>
    <li>Version: ${project_version}</li>
    <li>Repository: <a href="https://github.com/eclipse-ee4j/odi">eclipse-ee4j/odi</a></li>
    <li>ODI commit: <code>${odi_commit}</code></li>
    <li>Included Micronaut Core commit: <code>${micronaut_core_commit}</code></li>
  </ul>

  <h2>Specification And TCK</h2>
  <ul>
    <li>Specification: Jakarta Contexts Dependency Injection ${spec_version}</li>
    <li>Specification URL: <a href="https://jakarta.ee/specifications/cdi/${spec_version}/">https://jakarta.ee/specifications/cdi/${spec_version}/</a></li>
    <li>TCK: Jakarta CDI TCK ${cdi_version}</li>
    <li>TCK download: <a href="https://download.eclipse.org/ee4j/cdi/${spec_version}/cdi-tck-${cdi_version}-dist.zip">https://download.eclipse.org/ee4j/cdi/${spec_version}/cdi-tck-${cdi_version}-dist.zip</a></li>
    <li>TCK SHA-256: <code>446029ee1ce694d2a9ae8893d16be7afd7e1c0ed8705064b7095af174cf97ea0</code></li>
  </ul>

  <h2>Additional Certification Requirements</h2>
  <p>Signature-test evidence is pending; this workflow does not claim signature tests passed.</p>

  <h2>Environment</h2>
  <ul>
    <li>Workflow run: <a href="${workflow_url}">${workflow_url}</a></li>
    <li>Runner OS: ${RUNNER_OS:-local}</li>
    <li>Runner architecture: ${RUNNER_ARCH:-unknown}</li>
    <li>Java matrix version: ${java_version}</li>
  </ul>
  <pre><code>${java_runtime}</code></pre>

  <h2>Artifacts</h2>
  <ul>
    <li><a href="./summary.md">Markdown summary</a></li>
    <li><a href="./junit-xml/">Sanitized JUnit XML results</a></li>
  </ul>
  <p>Raw Gradle console output and unsanitized Gradle reports are intentionally not published because the build runs with secret-backed environment variables.</p>
</body>
</html>
EOF
