# Jenkins CI/CD Guide

이 문서는 이 저장소를 Jenkins로 빌드/테스트/배포하는 최소 구성을 정리합니다.

## 1) 포함된 파일

- 파이프라인: `Jenkinsfile`
- 서버 배포 스크립트: `scripts/deploy/deploy_app.sh`

## 2) Jenkins 준비

권장 Job 타입:

- Multibranch Pipeline

필수 플러그인:

- Pipeline
- Git
- Credentials Binding
- SSH Agent
- JUnit
- NodeJS
- ANSI Color

Jenkins Tool 설정:

- JDK: `temurin-17`
- NodeJS: `nodejs-22`

`Jenkinsfile`에서 위 이름을 그대로 참조합니다. 이름이 다르면 `Jenkinsfile`의 `environment` 값을 맞춰주세요.

## 3) 파이프라인 동작

기본 CI 단계:

1. Checkout
2. Toolchain 확인 (`java/node/npm`)
3. Backend test (`./gradlew clean test`)
4. Frontend lint/build (`npm ci && npm run lint && npm run build`)
5. Backend package (`./gradlew bootJar`)
6. Artifacts 보관 (jar, frontend dist)

CD(선택):

- 조건: `main` 브랜치 + `DEPLOY=true`
- 방식: SSH 원격 접속 후 `./scripts/deploy/deploy_app.sh` 실행

## 4) 배포 파라미터

- `DEPLOY`: 배포 실행 여부
- `DEPLOY_HOST`: 원격 서버 IP/호스트
- `DEPLOY_USER`: 원격 사용자
- `DEPLOY_DIR`: 원격 프로젝트 경로
- `DEPLOY_CREDENTIALS_ID`: Jenkins SSH Credential ID

예시:

- `DEPLOY=true`
- `DEPLOY_HOST=122.45.250.216`
- `DEPLOY_USER=juno`
- `DEPLOY_DIR=/home/juno/Workspace/btc-auto-trader`
- `DEPLOY_CREDENTIALS_ID=btc-prod-ssh`

## 5) 서버 권한 주의

`scripts/deploy/deploy_app.sh`는 내부에서 `sudo systemctl ...`를 사용합니다.  
Jenkins가 원격에서 실행할 때 아래 둘 중 하나가 필요합니다.

- 배포 사용자에 `NOPASSWD` sudo 허용
- 또는 배포 스크립트를 non-sudo 방식으로 분리

## 6) 트러블슈팅

- Node 버전 오류: NodeJS Tool이 22.x인지 확인
- SSH 배포 실패: Jenkins credential ID와 서버 `~/.ssh/authorized_keys` 확인
- 배포 시 sudo 프롬프트 발생: `sudoers` 정책 점검
