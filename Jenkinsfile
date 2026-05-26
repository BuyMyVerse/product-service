pipeline {
    agent {
        kubernetes {
            inheritFrom 'jenkins-agent'
            defaultContainer 'jnlp'
        }
    }

    environment {
        REMOTE_HOST   = "3.226.177.66"
        REMOTE_USER   = "admin"
        SSH_CRED_ID   = "jenkins-agent-ssh-key"
        PROJECT_PATH  = "/home/admin/Jenkins-deployment/product-service"

        NEXUS_HOST    = "dev-artifacthub.evaequitymtest.com"
        NEXUS_REPO    = "buymyverse-docker-dev"
        IMAGE_NAME    = "product-service"
        // Tag generated once and reused across all stages
        IMAGE_TAG     = "dev-${new Date().format('yyyy-MM-dd-HH-mm-ss')}"
        FULL_IMAGE    = "${NEXUS_HOST}/${NEXUS_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"
        NEXUS_CRED_ID = "nexus-credentials"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {

        // ── Stage 1: Deployment Notification ─────────────────────────────────
        stage('Deployment Notification') {
            steps {
                echo "========================================================"
                echo "  🚀 DEPLOYMENT STARTED"
                echo "========================================================"
                echo "  Job        : ${JOB_NAME}"
                echo "  Build No   : #${BUILD_NUMBER}"
                echo "  Branch     : ${GIT_BRANCH}"
                echo "  Image Tag  : ${IMAGE_TAG}"
                echo "  Full Image : ${FULL_IMAGE}"
                echo "  Target VM  : ${REMOTE_USER}@${REMOTE_HOST}"
                echo "  Source     : ${PROJECT_PATH}"
                echo "  Started At : ${new Date()}"
                echo "========================================================"
            }
        }

        // ── Stage 2: Git Checkout on VM ───────────────────────────────────────
        stage('Git Checkout') {
            steps {
                echo "📥 Pulling latest code on VM: branch ${GIT_BRANCH}"
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'
                            echo "=== Git Pull ==="
                            cd ${PROJECT_PATH}
                            git fetch --all
                            git checkout ${GIT_BRANCH}
                            git pull origin ${GIT_BRANCH}
                            echo "Branch  : \$(git branch --show-current)"
                            echo "Commit  : \$(git rev-parse --short HEAD)"
                            echo "Message : \$(git log -1 --pretty=%B)"
ENDSSH
                    """
                }
            }
        }

        // ── Stage 3: Docker Build ─────────────────────────────────────────────
        stage('Docker Build') {
            steps {
                echo "🐳 Building: ${FULL_IMAGE}"
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'
                            echo "=== Docker Build ==="
                            cd ${PROJECT_PATH}
                            echo "Building from: \$(pwd)"
                            echo "Dockerfile exists: \$(ls Dockerfile)"
                            docker build -t ${FULL_IMAGE} .
                            echo "=== Verify image exists ==="
                            docker images | grep ${IMAGE_NAME}
                            echo "✅ Build complete: ${FULL_IMAGE}"
ENDSSH
                    """
                }
            }
        }

        // ── Stage 4: Docker Push to Nexus ────────────────────────────────────
        stage('Docker Tag & Push to Nexus') {
            steps {
                echo "📤 Pushing: ${FULL_IMAGE}"
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: "${SSH_CRED_ID}",
                        keyFileVariable: 'SSH_KEY_FILE',
                        usernameVariable: 'SSH_USER'
                    ),
                    usernamePassword(
                        credentialsId: "${NEXUS_CRED_ID}",
                        usernameVariable: 'NEXUS_USER',
                        passwordVariable: 'NEXUS_PASS'
                    )
                ]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        # Pass credentials as env vars — avoids interpolation warning
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o SendEnv=NEXUS_USER \\
                            -o SendEnv=NEXUS_PASS \\
                            ${REMOTE_USER}@${REMOTE_HOST} \\
                            "
                            echo '=== Nexus Login ==='
                            echo '\$NEXUS_PASS' | docker login ${NEXUS_HOST} --username '\$NEXUS_USER' --password-stdin

                            echo '=== Verify image before push ==='
                            docker images | grep product-service

                            echo '=== Push to Nexus ==='
                            docker push ${FULL_IMAGE}

                            echo '=== Logout ==='
                            docker logout ${NEXUS_HOST}

                            echo '=== Cleanup local image ==='
                            docker rmi ${FULL_IMAGE} || true

                            echo '✅ Pushed: ${FULL_IMAGE}'
                            "
                    """
                }
            }
        }

        // ── Stage 5: kubectl get ns (via SSH on VM) ───────────────────────────
        stage('kubectl get ns') {
            steps {
                echo "☸️  Listing namespaces via VM..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'
                            echo "=== Kubernetes Namespaces ==="
                            kubectl get ns
ENDSSH
                    """
                }
            }
        }
    }

    post {
        success {
            echo "========================================================"
            echo "  ✅ DEPLOYMENT SUCCESS"
            echo "  Image : ${FULL_IMAGE}"
            echo "  Build : #${BUILD_NUMBER}"
            echo "========================================================"
        }
        failure {
            echo "========================================================"
            echo "  ❌ DEPLOYMENT FAILED — Build #${BUILD_NUMBER}"
            echo "  Check console output above for details."
            echo "========================================================"
        }
        always {
            echo "🧹 Cleaning workspace..."
            cleanWs()
        }
    }
}
