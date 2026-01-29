pipeline {
  agent any
  options { timestamps() }
  parameters {
    string(name: 'REGISTRY', defaultValue: '', description: 'Container registry (e.g. ghcr.io/owner)')
    string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'Docker image tag')
    string(name: 'DOCKER_CREDENTIALS_ID', defaultValue: '', description: 'Jenkins credentials id for registry login')
    choice(name: 'DEPLOY_TARGET', choices: ['none', 'compose', 'k8s'], description: 'Deployment target')
    string(name: 'SSH_HOST', defaultValue: '', description: 'Compose deploy host')
    string(name: 'SSH_USER', defaultValue: 'ubuntu', description: 'Compose deploy user')
    string(name: 'SSH_PORT', defaultValue: '22', description: 'Compose deploy SSH port')
    string(name: 'SSH_CREDENTIALS_ID', defaultValue: '', description: 'Jenkins SSH credentials id')
    string(name: 'REMOTE_APP_DIR', defaultValue: '/opt/btc-auto-trader', description: 'Remote app root')
    string(name: 'ENV_FILE_CREDENTIALS_ID', defaultValue: '', description: 'Jenkins secret file id for .env.prod')
    booleanParam(name: 'SYNC_INFRA', defaultValue: true, description: 'Sync infra folder before compose deploy')
    string(name: 'REMOTE_DOCKER_CMD', defaultValue: 'docker', description: 'Remote docker command (e.g. sudo docker)')
    string(name: 'HEALTHCHECK_URL', defaultValue: 'http://localhost/api/actuator/health', description: 'Backend health URL')
    string(name: 'FRONTEND_URL', defaultValue: 'http://localhost/', description: 'Frontend URL')
    string(name: 'HEALTHCHECK_RETRIES', defaultValue: '12', description: 'Healthcheck retries')
    string(name: 'HEALTHCHECK_INTERVAL', defaultValue: '5', description: 'Healthcheck interval seconds')
    booleanParam(name: 'ROLLBACK_ON_FAIL', defaultValue: true, description: 'Rollback on failed healthcheck')
    string(name: 'KUBE_CONTEXT', defaultValue: '', description: 'kubectl context name')
    string(name: 'K8S_NAMESPACE', defaultValue: 'default', description: 'Kubernetes namespace')
    string(name: 'KUSTOMIZE_OVERLAY', defaultValue: 'k8s/overlays/prod', description: 'Kustomize overlay path')
    string(name: 'K8S_SECRETS_CREDENTIALS_ID', defaultValue: '', description: 'Jenkins secret file id for kustomize secrets.env')
    string(name: 'K8S_ROLLOUT_TIMEOUT', defaultValue: '120s', description: 'Rollout status timeout')
  }
  stages {
    stage('Backend: test & build') {
      steps {
        dir('backend') {
          script {
            if (isUnix()) {
              sh './gradlew test'
              sh './gradlew bootJar'
            } else {
              bat 'gradlew.bat test'
              bat 'gradlew.bat bootJar'
            }
          }
        }
      }
    }
    stage('Frontend: build') {
      steps {
        dir('frontend') {
          script {
            if (isUnix()) {
              sh 'npm install'
              sh 'npm run build'
            } else {
              bat 'npm install'
              bat 'npm run build'
            }
          }
        }
      }
    }
    stage('Docker login') {
      when {
        expression { params.REGISTRY?.trim() && params.DOCKER_CREDENTIALS_ID?.trim() }
      }
      steps {
        script {
          def registryHost = params.REGISTRY.split('/')[0]
          withCredentials([usernamePassword(credentialsId: params.DOCKER_CREDENTIALS_ID, usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
            if (isUnix()) {
              sh "echo \\\"$REG_PASS\\\" | docker login ${registryHost} -u \\\"$REG_USER\\\" --password-stdin"
            } else {
              bat "echo %REG_PASS% | docker login ${registryHost} -u %REG_USER% --password-stdin"
            }
          }
        }
      }
    }
    stage('Docker build') {
      steps {
        script {
          def backendImage = params.REGISTRY ? "${params.REGISTRY}/btc-backend:${params.IMAGE_TAG}" : "btc-backend:${params.IMAGE_TAG}"
          def frontendImage = params.REGISTRY ? "${params.REGISTRY}/btc-frontend:${params.IMAGE_TAG}" : "btc-frontend:${params.IMAGE_TAG}"

          if (isUnix()) {
            sh "docker build -t ${backendImage} backend"
            sh "docker build -t ${frontendImage} frontend"
          } else {
            bat "docker build -t ${backendImage} backend"
            bat "docker build -t ${frontendImage} frontend"
          }

          if (params.REGISTRY?.trim()) {
            if (isUnix()) {
              sh "docker push ${backendImage}"
              sh "docker push ${frontendImage}"
            } else {
              bat "docker push ${backendImage}"
              bat "docker push ${frontendImage}"
            }
          } else {
            echo 'REGISTRY not set. Skipping docker push.'
          }
        }
      }
    }
    stage('Deploy') {
      when {
        expression { params.DEPLOY_TARGET != 'none' }
      }
      steps {
        script {
          def backendImage = params.REGISTRY ? "${params.REGISTRY}/btc-backend:${params.IMAGE_TAG}" : "btc-backend:${params.IMAGE_TAG}"
          def frontendImage = params.REGISTRY ? "${params.REGISTRY}/btc-frontend:${params.IMAGE_TAG}" : "btc-frontend:${params.IMAGE_TAG}"

          if (params.DEPLOY_TARGET == 'compose') {
            if (!params.SSH_HOST?.trim() || !params.SSH_CREDENTIALS_ID?.trim()) {
              error 'SSH_HOST and SSH_CREDENTIALS_ID are required for compose deploy.'
            }
            def deployArgsSh = "--host ${params.SSH_HOST} --user ${params.SSH_USER} --port ${params.SSH_PORT} --dir ${params.REMOTE_APP_DIR}" +
              " --backend-image ${backendImage} --frontend-image ${frontendImage}" +
              " --health-url ${params.HEALTHCHECK_URL} --frontend-url ${params.FRONTEND_URL}" +
              " --health-retries ${params.HEALTHCHECK_RETRIES} --health-interval ${params.HEALTHCHECK_INTERVAL}" +
              " --docker-cmd ${params.REMOTE_DOCKER_CMD} --compose-project btc-trader"
            def deployArgsPs = "-TargetHost ${params.SSH_HOST} -User ${params.SSH_USER} -Port ${params.SSH_PORT} -RemoteDir ${params.REMOTE_APP_DIR}" +
              " -BackendImage ${backendImage} -FrontendImage ${frontendImage}" +
              " -HealthUrl ${params.HEALTHCHECK_URL} -FrontendUrl ${params.FRONTEND_URL}" +
              " -HealthRetries ${params.HEALTHCHECK_RETRIES} -HealthInterval ${params.HEALTHCHECK_INTERVAL}" +
              " -DockerCmd ${params.REMOTE_DOCKER_CMD} -ComposeProject btc-trader"
            if (params.ROLLBACK_ON_FAIL) {
              deployArgsSh += " --rollback-on-fail"
              deployArgsPs += " -RollbackOnFail"
            }
            if (!params.SYNC_INFRA) {
              deployArgsSh += " --skip-sync"
              deployArgsPs += " -SkipSync"
            }
            sshagent(credentials: [params.SSH_CREDENTIALS_ID]) {
              def envInjected = false
              if (params.ENV_FILE_CREDENTIALS_ID?.trim()) {
                withCredentials([file(credentialsId: params.ENV_FILE_CREDENTIALS_ID, variable: 'ENV_FILE')]) {
                  if (isUnix()) {
                    sh "cp $ENV_FILE infra/.env.prod"
                  } else {
                    bat "copy %ENV_FILE% infra\\.env.prod"
                  }
                }
                envInjected = true
                deployArgsSh += " --env-file infra/.env.prod --copy-env"
                deployArgsPs += " -EnvFile infra/.env.prod -CopyEnv"
              }
              try {
                if (isUnix()) {
                  sh "./scripts/deploy-compose-remote.sh ${deployArgsSh}"
                } else {
                  powershell ".\\\\scripts\\\\deploy-compose-remote.ps1 ${deployArgsPs}"
                }
              } finally {
                if (envInjected) {
                  if (isUnix()) {
                    sh "rm -f infra/.env.prod"
                  } else {
                    bat "del /f /q infra\\.env.prod"
                  }
                }
              }
            }
          }

          if (params.DEPLOY_TARGET == 'k8s') {
            def ctx = params.KUBE_CONTEXT?.trim() ? "--context ${params.KUBE_CONTEXT}" : ""
            def ns = params.K8S_NAMESPACE?.trim() ? "-n ${params.K8S_NAMESPACE}" : ""
            if (params.K8S_SECRETS_CREDENTIALS_ID?.trim()) {
              withCredentials([file(credentialsId: params.K8S_SECRETS_CREDENTIALS_ID, variable: 'K8S_SECRETS_FILE')]) {
                if (isUnix()) {
                  sh "cp $K8S_SECRETS_FILE ${params.KUSTOMIZE_OVERLAY}/secrets.env"
                } else {
                  bat "copy %K8S_SECRETS_FILE% ${params.KUSTOMIZE_OVERLAY}\\\\secrets.env"
                }
              }
            }
            try {
              if (isUnix()) {
                sh "kubectl ${ctx} ${ns} apply -k ${params.KUSTOMIZE_OVERLAY}"
                sh "kubectl ${ctx} ${ns} set image deployment/btc-backend backend=${backendImage}"
                sh "kubectl ${ctx} ${ns} set image deployment/btc-frontend frontend=${frontendImage}"
                sh "kubectl ${ctx} ${ns} rollout status deployment/btc-backend --timeout=${params.K8S_ROLLOUT_TIMEOUT}"
                sh "kubectl ${ctx} ${ns} rollout status deployment/btc-frontend --timeout=${params.K8S_ROLLOUT_TIMEOUT}"
              } else {
                bat "kubectl ${ctx} ${ns} apply -k ${params.KUSTOMIZE_OVERLAY}"
                bat "kubectl ${ctx} ${ns} set image deployment/btc-backend backend=${backendImage}"
                bat "kubectl ${ctx} ${ns} set image deployment/btc-frontend frontend=${frontendImage}"
                bat "kubectl ${ctx} ${ns} rollout status deployment/btc-backend --timeout=${params.K8S_ROLLOUT_TIMEOUT}"
                bat "kubectl ${ctx} ${ns} rollout status deployment/btc-frontend --timeout=${params.K8S_ROLLOUT_TIMEOUT}"
              }
            } catch (err) {
              if (params.ROLLBACK_ON_FAIL) {
                if (isUnix()) {
                  sh "kubectl ${ctx} ${ns} rollout undo deployment/btc-backend"
                  sh "kubectl ${ctx} ${ns} rollout undo deployment/btc-frontend"
                } else {
                  bat "kubectl ${ctx} ${ns} rollout undo deployment/btc-backend"
                  bat "kubectl ${ctx} ${ns} rollout undo deployment/btc-frontend"
                }
              }
              throw err
            } finally {
              if (params.K8S_SECRETS_CREDENTIALS_ID?.trim()) {
                if (isUnix()) {
                  sh "rm -f ${params.KUSTOMIZE_OVERLAY}/secrets.env"
                } else {
                  bat "del /f /q ${params.KUSTOMIZE_OVERLAY}\\\\secrets.env"
                }
              }
            }
          }
        }
      }
    }
  }
}
