# k8s-jenkins

Ansible playbook to build a k8s Jenkins based cicd.
Requires:
- Helm3

## Helm command

```shell
helm list -A
helm -n ci get values jenkins
helm show values jenkins
```

- https://www.youtube.com/watch?v=KKQWXtRmxcE

## Jenkins

```shell
kubectl patch svc jenkins -n ci --type='json' -p '[{"op":"replace","path":"/spec/type","value":"NodePort"}]'        
```

## Grafana

- https://grafana.com/docs/grafana/latest/setup-grafana/installation/helm/