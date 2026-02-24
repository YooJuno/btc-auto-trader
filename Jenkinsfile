pipeline {
  agent any

  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '40', artifactNumToKeepStr: '20'))
    timeout(time: 45, unit: 'MINUTES')
    timestamps()
    ansiColor('xterm')
  }

  parameters {
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'main 브랜치 빌드 성공 후 원격 서버 배포')
    string(name: 'DEPLOY_HOST', defaultValue: '', description: '예: 122.45.250.216')
    string(name: 'DEPLOY_USER', defaultValue: 'juno', description: '원격 서버 사용자')
    string(name: 'DEPLOY_DIR', defaultValue: '/home/juno/Workspace/btc-auto-trader', description: '원격 프로젝트 경로')
    string(name: 'DEPLOY_CREDENTIALS_ID', defaultValue: 'btc-prod-ssh', description: 'Jenkins SSH Credential ID')
  }

  environment {
    JAVA_HOME = tool(name: 'temurin-17', type: 'hudson.model.JDK')
    NODEJS_HOME = tool(name: 'nodejs-22', type: 'jenkins.plugins.nodejs.tools.NodeJSInstallation')
    PATH = "${env.JAVA_HOME}/bin:${env.NODEJS_HOME}/bin:${env.PATH}"
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Verify Toolchain') {
      steps {
        sh '''
          set -euo pipefail
          java -version
          node -v
          npm -v
        '''
      }
    }

    stage('Backend Test') {
      steps {
        sh '''
          set -euo pipefail
          cd apps/backend
          ./gradlew --no-daemon clean test
        '''
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: 'apps/backend/build/test-results/test/*.xml'
        }
      }
    }

    stage('Frontend Lint & Build') {
      steps {
        sh '''
          set -euo pipefail
          cd apps/frontend
          npm ci
          npm run lint
          npm run build
        '''
      }
    }

    stage('Backend Package') {
      steps {
        sh '''
          set -euo pipefail
          cd apps/backend
          ./gradlew --no-daemon bootJar
        '''
      }
    }

    stage('Archive Artifacts') {
      steps {
        archiveArtifacts artifacts: 'apps/backend/build/libs/*.jar,apps/frontend/dist/**', allowEmptyArchive: false, fingerprint: true
      }
    }

    stage('Deploy') {
      when {
        allOf {
          branch 'main'
          expression { return params.DEPLOY }
        }
      }
      steps {
        script {
          if (!params.DEPLOY_HOST?.trim()) {
            error('DEPLOY_HOST 값이 비어 있습니다.')
          }
        }
        sshagent(credentials: [params.DEPLOY_CREDENTIALS_ID]) {
          sh '''
            set -euo pipefail
            ssh -o StrictHostKeyChecking=no "${DEPLOY_USER}@${DEPLOY_HOST}" "
              set -euo pipefail
              cd '${DEPLOY_DIR}'
              git fetch --all --prune
              git checkout '${BRANCH_NAME}'
              git pull --ff-only
              ./scripts/build.sh
            "
          '''
        }
      }
    }
  }

  post {
    success {
      echo 'Pipeline succeeded.'
    }
    failure {
      echo 'Pipeline failed.'
    }
  }
}
