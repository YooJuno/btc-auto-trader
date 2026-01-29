pipeline {
  agent any
  options { timestamps() }
  parameters {
    string(name: 'REGISTRY', defaultValue: '', description: 'Container registry (e.g. ghcr.io/owner)')
    string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'Docker image tag')
    choice(name: 'DEPLOY_TARGET', choices: ['none', 'compose', 'k8s'], description: 'Deployment target')
    string(name: 'SSH_HOST', defaultValue: '', description: 'Compose deploy host')
    string(name: 'SSH_USER', defaultValue: 'ubuntu', description: 'Compose deploy user')
    string(name: 'SSH_PORT', defaultValue: '22', description: 'Compose deploy SSH port')
    string(name: 'SSH_CREDENTIALS_ID', defaultValue: '', description: 'Jenkins SSH credentials id')
    string(name: 'REMOTE_APP_DIR', defaultValue: '/opt/btc-auto-trader', description: 'Remote app root')
    string(name: 'KUBE_CONTEXT', defaultValue: '', description: 'kubectl context name')
    string(name: 'K8S_NAMESPACE', defaultValue: 'default', description: 'Kubernetes namespace')
    string(name: 'KUSTOMIZE_OVERLAY', defaultValue: 'k8s/overlays/prod', description: 'Kustomize overlay path')
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
            def remote = "${params.SSH_USER}@${params.SSH_HOST}"
            def portOpt = params.SSH_PORT?.trim() ? "-p ${params.SSH_PORT}" : ""
            def cmd = "cd ${params.REMOTE_APP_DIR}/infra && BACKEND_IMAGE=${backendImage} FRONTEND_IMAGE=${frontendImage} docker compose -f docker-compose.prod.registry.yml --env-file .env.prod up -d"
            sshagent(credentials: [params.SSH_CREDENTIALS_ID]) {
              if (isUnix()) {
                sh "ssh ${portOpt} ${remote} '${cmd}'"
              } else {
                bat "ssh ${portOpt} ${remote} \"${cmd}\""
              }
            }
          }

          if (params.DEPLOY_TARGET == 'k8s') {
            def ctx = params.KUBE_CONTEXT?.trim() ? "--context ${params.KUBE_CONTEXT}" : ""
            def ns = params.K8S_NAMESPACE?.trim() ? "-n ${params.K8S_NAMESPACE}" : ""
            if (isUnix()) {
              sh "kubectl ${ctx} ${ns} apply -k ${params.KUSTOMIZE_OVERLAY}"
              sh "kubectl ${ctx} ${ns} set image deployment/btc-backend backend=${backendImage}"
              sh "kubectl ${ctx} ${ns} set image deployment/btc-frontend frontend=${frontendImage}"
            } else {
              bat "kubectl ${ctx} ${ns} apply -k ${params.KUSTOMIZE_OVERLAY}"
              bat "kubectl ${ctx} ${ns} set image deployment/btc-backend backend=${backendImage}"
              bat "kubectl ${ctx} ${ns} set image deployment/btc-frontend frontend=${frontendImage}"
            }
          }
        }
      }
    }
  }
}
