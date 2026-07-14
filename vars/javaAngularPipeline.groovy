def call(Map config = [:]) {
    def frontendUrl = config.frontendUrl   // optional — skip frontend stages if absent
    def backendUrl  = config.backendUrl    ?: error('backendUrl is required')
    def baseHref    = config.baseHref      ?: '/'
    def imageRepo   = config.imageRepo     ?: error('imageRepo is required')
    def appName     = config.appName       ?: error('appName is required')

    pipeline {
        agent {
            kubernetes {
                yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: node
      image: node:19
      command: [cat]
      tty: true
      resources:
        requests:
          cpu: 200m
          memory: 512Mi
        limits:
          cpu: "1"
          memory: 2Gi
      volumeMounts:
        - mountPath: /root/jenkins_home
          name: jenkins-m2-cache
    - name: maven
      image: maven:3-eclipse-temurin-25
      command: [cat]
      tty: true
      resources:
        requests:
          cpu: 200m
          memory: 512Mi
        limits:
          cpu: "1"
          memory: 2Gi
      env:
        - name: SONAR_TOKEN
          valueFrom:
            secretKeyRef:
              name: sonarqube-token
              key: token
      volumeMounts:
        - mountPath: /root/jenkins_home
          name: jenkins-m2-cache
    - name: sonar-scanner
      image: sonarsource/sonar-scanner-cli
      command: [cat]
      tty: true
      resources:
        requests:
          cpu: 100m
          memory: 256Mi
        limits:
          cpu: 500m
          memory: 512Mi
      env:
        - name: SONAR_TOKEN
          valueFrom:
            secretKeyRef:
              name: sonarqube-token
              key: token
    - name: kaniko
      image: gcr.io/kaniko-project/executor:debug
      command: [sleep]
      args: [99d]
      resources:
        requests:
          cpu: 200m
          memory: 256Mi
        limits:
          cpu: "1"
          memory: 1Gi
      volumeMounts:
        - mountPath: /kaniko/.docker
          name: kaniko-secret
          readOnly: true
    - name: kubectl
      image: docker.io/bitnami/kubectl
      command: [cat]
      tty: true
      resources:
        requests:
          cpu: 50m
          memory: 64Mi
        limits:
          cpu: 200m
          memory: 128Mi
      securityContext:
        runAsUser: 1000
  volumes:
    - name: jenkins-m2-cache
      persistentVolumeClaim:
        claimName: jenkins
        readOnly: false
    - name: kaniko-secret
      secret:
        secretName: kaniko-config
"""
            }
        }
        stages {
            stage('FrontEnd') {
                when { expression { frontendUrl != null } }
                steps {
                    dir('frontend') {
                        container('node') {
                            git branch: env.BRANCH_NAME, changelog: false, poll: false, url: frontendUrl
                            sh 'cp -r /root/jenkins_home/node_modules . || true'
                            sh 'npm install'
                            sh 'npm install -g @angular/cli@19'
                            sh "ng build --base-href ${baseHref}"
                            sh 'cp -r node_modules/ /root/jenkins_home/'
                        }
                        container('maven') {
                            sh 'mvn -Dmaven.repo.local=/root/jenkins_home/.m2 clean install'
                        }
                    }
                }
            }
            stage('Backend') {
                steps {
                    dir('backend') {
                        container('maven') {
                            git branch: env.BRANCH_NAME, changelog: false, poll: false, url: backendUrl
                            sh 'mvn -Dmaven.repo.local=/root/jenkins_home/.m2 clean install'
                        }
                    }
                }
            }
            stage('SonarQube') {
                steps {
                    script {
                        dir('backend') {
                            container('maven') {
                                withSonarQubeEnv('SonarQube') {
                                    sh "mvn -Dmaven.repo.local=/root/jenkins_home/.m2 sonar:sonar -Dsonar.projectKey=${appName}-backend -Dsonar.token=\${SONAR_TOKEN}"
                                }
                            }
                        }
                        timeout(time: 5, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                        if (frontendUrl) {
                            dir('frontend') {
                                container('sonar-scanner') {
                                    withSonarQubeEnv('SonarQube') {
                                        sh """sonar-scanner \
                                            -Dsonar.projectKey=${appName}-frontend \
                                            -Dsonar.sources=src \
                                            -Dsonar.exclusions=node_modules/**,dist/** \
                                            -Dsonar.token=\${SONAR_TOKEN}"""
                                    }
                                }
                            }
                            timeout(time: 5, unit: 'MINUTES') {
                                waitForQualityGate abortPipeline: true
                            }
                        }
                    }
                }
            }
            stage('Package') {
                steps {
                    dir('backend') {
                        container('maven') {
                            sh 'sed -e "s/CURRENT_GIT_COMMIT/$GIT_COMMIT/g" run/deployment.yml > deployment.yml'
                        }
                        container('kaniko') {
                            sh "/kaniko/executor --context `pwd` --dockerfile Dockerfile --customPlatform=linux/amd64 --destination ${imageRepo}:\${GIT_COMMIT}"
                        }
                    }
                }
            }
            stage('Deploy') {
                steps {
                    dir('backend') {
                        container('kubectl') {
                            sh 'kubectl apply -f deployment.yml'
                            sh "kubectl wait --for=condition=available deployment/${appName} -n run"
                            script {
                                def nodePort = sh(
                                    returnStdout: true,
                                    script: "kubectl get svc ${appName} -n run -o jsonpath='{.spec.ports[0].nodePort}'"
                                ).trim()
                                echo "http://localhost:${nodePort}${baseHref}"
                            }
                        }
                    }
                }
            }
        }
    }
}
