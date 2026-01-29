pipeline {
  agent any
  options { timestamps() }
  parameters {
    string(name: 'REGISTRY', defaultValue: '', description: 'Container registry (e.g. ghcr.io/owner)')
    string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'Docker image tag')
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
  }
}
