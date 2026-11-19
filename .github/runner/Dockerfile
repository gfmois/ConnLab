# Self-hosted GitHub Actions runner for this repository, in a container.
# The jobs download the JDK (setup-java) and the Qodana analyzer (native mode,
# no Docker needed) into the runner's home (a volume) the first time and reuse
# them. See compose.yaml.
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
SHELL ["/bin/bash", "-euo", "pipefail", "-c"]

RUN apt-get update \
 && apt-get install -y -q --no-install-recommends \
      ca-certificates curl git jq tar unzip zip fontconfig

# The runner, checked against the SHA-256 in its release notes.
RUN useradd --create-home --shell /bin/bash runner \
 && mkdir /opt/actions-runner && cd /opt/actions-runner \
 && release="$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest)" \
 && v="$(jq -r .tag_name <<< "$release")" && v="${v#v}" \
 && sha="$(jq -r .body <<< "$release" | sed -n 's/.*<!-- BEGIN SHA linux-x64 -->\([0-9a-f]\{64\}\)<!-- END SHA linux-x64 -->.*/\1/p')" \
 && [ -n "$sha" ] \
 && curl -fsSL -o runner.tar.gz "https://github.com/actions/runner/releases/download/v$v/actions-runner-linux-x64-$v.tar.gz" \
 && echo "$sha  runner.tar.gz" | sha256sum -c - \
 && tar -xzf runner.tar.gz && rm runner.tar.gz \
 && ./bin/installdependencies.sh \
 && chown -R runner:runner /opt/actions-runner \
 && rm -rf /var/lib/apt/lists/*

COPY --chmod=755 entrypoint.sh /usr/local/bin/entrypoint.sh
USER runner
WORKDIR /opt/actions-runner
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
