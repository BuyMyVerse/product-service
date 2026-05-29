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
        BRANCH_NAME   = "dev"

        NEXUS_HOST    = "dev-artifacthub.evaequitymtest.com"
        NEXUS_REPO    = "buymyverse-docker-dev"
        IMAGE_NAME    = "product-service"
        IMAGE_TAG     = "dev-${new Date().format('yyyy-MM-dd-HH-mm-ss')}"
        FULL_IMAGE    = "${NEXUS_HOST}/${NEXUS_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"
        NEXUS_CRED_ID = "nexus-credentials"

        K8S_NAMESPACE  = "buymyverse-dev"
        K8S_DEPLOYMENT = "product-service"
        K8S_CONTAINER  = "product-service"
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
                echo "  Branch     : ${BRANCH_NAME}"
                echo "  Image Tag  : ${IMAGE_TAG}"
                echo "  Full Image : ${FULL_IMAGE}"
                echo "  Target VM  : ${REMOTE_USER}@${REMOTE_HOST}"
                echo "  EKS NS     : ${K8S_NAMESPACE}"
                echo "  Started At : ${new Date()}"
                echo "========================================================"
            }
        }

        // ── Stage 2: Git Checkout on VM ───────────────────────────────────────
        stage('Git Checkout') {
            steps {
                echo "📥 Pulling latest: branch ${BRANCH_NAME}"
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE -o StrictHostKeyChecking=no \\
                            ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'
                            cd ${PROJECT_PATH}
                            echo "=== Git Pull ==="
                            git fetch --all
                            git checkout ${BRANCH_NAME}
                            git pull origin ${BRANCH_NAME}
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
            when {
                allOf {
                    not { changeRequest() }
                    branch 'qa'
                }
            }
            steps {
                echo "🐳 Building: ${FULL_IMAGE}"
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE -o StrictHostKeyChecking=no \\
                            ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'
                            cd ${PROJECT_PATH}
                            echo "=== Docker Build ==="
                            docker build --no-cache -t ${FULL_IMAGE} .
                            echo "=== Verify image ==="
                            docker images | grep ${IMAGE_NAME}
                            echo "✅ Build complete: ${FULL_IMAGE}"
ENDSSH
                    """
                }
            }
        }

        // ── Stage 4: Docker Push to Nexus ─────────────────────────────────────
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
                        ssh -i \$SSH_KEY_FILE -o StrictHostKeyChecking=no \\
                            ${REMOTE_USER}@${REMOTE_HOST} bash -e << ENDSSH
                            echo "=== Nexus Login ==="
                            echo "\$NEXUS_PASS" | docker login ${NEXUS_HOST} --username "\$NEXUS_USER" --password-stdin

                            echo "=== Push to Nexus ==="
                            docker push ${FULL_IMAGE}

                            echo "=== Logout ==="
                            docker logout ${NEXUS_HOST}

                            echo "=== Cleanup local image ==="
                            docker rmi ${FULL_IMAGE} || true

                            echo "✅ Pushed: ${FULL_IMAGE}"
ENDSSH
                    """
                }
            }
        }

        // ── Stage 5: kubectl get ns ───────────────────────────────────────────
        stage('kubectl get ns') {
            steps {
                echo "☸️  Listing Kubernetes namespaces..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE -o StrictHostKeyChecking=no \\
                            ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'
                            echo "=== Kubernetes Namespaces ==="
                            kubectl get ns
ENDSSH
                    """
                }
            }
        }

        // ── Stage 6: Deploy to EKS ────────────────────────────────────────────
        stage('Deploy to EKS') {
            steps {
                echo "☸️  Deploying to EKS: ${K8S_NAMESPACE}/${K8S_DEPLOYMENT}"
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE -o StrictHostKeyChecking=no \\
                            ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'
                            echo "=== Pods before deploy ==="
                            kubectl get pods -n ${K8S_NAMESPACE} | grep ${K8S_DEPLOYMENT}

                            echo "=== Updating image ==="
                            kubectl set image deployment/${K8S_DEPLOYMENT} \\
                                ${K8S_CONTAINER}=${FULL_IMAGE} \\
                                -n ${K8S_NAMESPACE}

                            echo "=== Waiting for rollout ==="
                            kubectl rollout status deployment/${K8S_DEPLOYMENT} \\
                                -n ${K8S_NAMESPACE} --timeout=120s

                            echo "=== Pods after deploy ==="
                            kubectl get pods -n ${K8S_NAMESPACE} | grep ${K8S_DEPLOYMENT}

                            echo "=== Image now running ==="
                            kubectl get deployment ${K8S_DEPLOYMENT} -n ${K8S_NAMESPACE} \\
                                -o=jsonpath='{.spec.template.spec.containers[0].image}'
                            echo ""
                            echo "✅ Deployed: ${FULL_IMAGE}"
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
            echo "  Image     : ${FULL_IMAGE}"
            echo "  Namespace : ${K8S_NAMESPACE}"
            echo "  Build     : #${BUILD_NUMBER}"
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
