# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Ansible playbook that provisions a complete Kubernetes CI/CD stack using Helm charts. Runs entirely against `localhost` (connection: local), targeting an existing local Kubernetes cluster.

## Commands

The venv must be activated before running any commands. `make activate` installs dependencies into the venv, but venv activation does not persist across make targets — activate manually first:

```shell
source .venv/bin/activate
pip install -r requirement.txt
```

Then deploy or destroy the full stack:

```shell
ansible-playbook ansible-playbook.yml --extra-vars="mode=deploy"
ansible-playbook ansible-playbook.yml --extra-vars="mode=destroy"
```

Or via Make (requires venv already activated):

```shell
make install     # deploy
make uninstall   # destroy
```

Run a single role without executing others by temporarily adjusting `ansible-playbook.yml` or using `--tags` if tags are added, or by passing `--start-at-task`.

Useful Helm/kubectl commands for inspection:

```shell
helm list -A
helm -n ci get values jenkins
kubectl patch svc jenkins -n ci --type='json' -p '[{"op":"replace","path":"/spec/type","value":"NodePort"}]'
kubectl get secret --namespace ci my-grafana -o jsonpath="{.data.admin-password}" | base64 --decode
```

## Architecture

### Flow

`ansible-playbook.yml` → runs roles in sequence against localhost → each role uses `kubernetes.core` Helm/k8s modules to install charts and Kubernetes resources.

All roles gate their tasks on the `mode` variable (`deploy` or `destroy`). `mode` must be passed as an extra var.

### Role Execution Order

1. **cluster** — creates namespaces (`ci`, `run`) and RBAC; installs `kubernetes.core` collection
2. **jenkins** — installs Jenkins via Helm with JCasC; creates kaniko Docker secret; prints admin password
3. **loki** — installs `grafana/loki-stack` (Loki + Promtail)
4. **prometheus** — installs `prometheus-community/kube-prometheus-stack` (with Grafana disabled to avoid conflict)
5. **grafana** — installs `grafana-community/grafana` with Prometheus and Loki datasources pre-wired; imports Kubernetes dashboard (gnetId 315)
6. **ingress** — installs `ingress-nginx` and creates an Ingress routing `/jenkins` → Jenkins and `/grafana` → Grafana

Roles **nexus** and **dashboard** exist in `roles/` but are not active (nexus is absent from the playbook; dashboard is commented out).

### Configuration

All service names, ports, and Kubernetes paths are defined centrally in `group_vars/all.yml`. Roles reference these as `{{ jenkins.port }}`, `{{ grafana.path }}`, etc. The two namespaces are `ci` (build workloads) and `run` (deployed applications).

### RBAC

The `cluster` role grants the `default` ServiceAccount in the `ci` namespace a `deployment-manager` Role in the `run` namespace, allowing Jenkins pipelines to deploy applications there.

### Kaniko (Docker image builds)

`roles/jenkins/vars/main.yml` holds DockerHub credentials. The `kaniko` task templates `config.json.j2` into a local `config.json`, then stores it as the `kaniko-config` Kubernetes Secret in the `ci` namespace. Jenkins pipeline pods mount this secret to push images.

### Jenkins Configuration as Code (JCasC)

Jenkins is configured entirely via the Helm chart's `JCasC.configScripts` values (in `roles/jenkins/tasks/install.yml`). This includes the system message and a pre-seeded multibranch pipeline job pointing to `https://github.com/erwanjouan/test-tool-backend.git` using `cicd/Jenkinsfile`.
