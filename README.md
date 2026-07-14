# k8s-jenkins

Ansible playbook to build a k8s cicd chain for Java.
Requires:
- Docker-Desktop
- Helm3

## Helm command

```shell
helm list -A
helm -n ci get values jenkins
helm show values jenkins
```

- https://www.youtube.com/watch?v=KKQWXtRmxcE

## DNS setup (required before first deploy)

The ingress uses host-based routing. Add the following entry to `/etc/hosts` so that `jenkins.local` and `grafana.local` resolve to the Docker Desktop ingress:

```shell
sudo sh -c 'echo "127.0.0.1   jenkins.local grafana.local nexus.local" >> /etc/hosts'
```

After deploy, services are available at:
- http://jenkins.local
- http://grafana.local
- http://nexus.local

## Grafana

- https://grafana.com/docs/grafana/latest/setup-grafana/installation/helm/