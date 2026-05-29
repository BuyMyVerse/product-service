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

        // ── Stage 1: Notification ─────────────────────────────────────────────
        stage('Build Notification') {
            steps {
                script {
                    if (env.CHANGE_ID) {
                        echo "========================================================"
                        echo "  🔍 PR VALIDATION BUILD"
                        echo "========================================================"
                        echo "  PR Number  : #${env.CHANGE_ID}"
                        echo "  PR Title   : ${env.CHANGE_TITLE}"
                        echo "  Source     : ${env.CHANGE_BRANCH} → ${env.CHANGE_TARGET}"
                        echo "  Author     : ${env.CHANGE_AUTHOR}"
                        echo "  Build No   : #${BUILD_NUMBER}"
                        echo "  Started At : ${new Date()}"
                        echo "========================================================"
                        echo "  Stages     : Checkout → Docker Build → Cleanup"
                        echo "  Skipped    : Push to Nexus, kubectl, Deploy to EKS"
                        echo "========================================================"
                    } else {
                        echo "========================================================"
                        echo "  🚀 FULL DEPLOYMENT BUILD"
                        echo "========================================================"
                        echo "  Job        : ${JOB_NAME}"
                        echo "  Build No   : #${BUILD_NUMBER}"
                        echo "  Branch     : ${env.BRANCH_NAME}"
                        echo "  Image Tag  : ${IMAGE_TAG}"
                        echo "  Full Image : ${FULL_IMAGE}"
                        echo "  Target VM  : ${REMOTE_USER}@${REMOTE_HOST}"
                        echo "  EKS NS     : ${K8S_NAMESPACE}"
                        echo "  Started At : ${new Date()}"
                        echo "========================================================"
                        echo "  Stages     : Checkout → Build → Push → Deploy to EKS"
                        echo "========================================================"
                    }
                }
            }
        }

        // ── Stage 2: Git Checkout ─────────────────────────────────────────────
        // PR build  → checks out the feature branch
        // Merge build → checks out dev
        stage('Git Checkout') {
            steps {
                script {
                    def targetBranch = env.CHANGE_ID ? env.CHANGE_BRANCH : env.BRANCH_NAME
                    echo "📥 Checking out branch: ${targetBranch}"
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
                                echo "=== Git Fetch ==="
                                git fetch --all
                                git checkout ${targetBranch}
                                git pull origin ${targetBranch}
                                echo "Branch  : \$(git branch --show-current)"
                                echo "Commit  : \$(git rev-parse --short HEAD)"
                                echo "Message : \$(git log -1 --pretty=%B)"
ENDSSH
                        """
                    }
                }
            }
        }

        // ── Stage 3: Docker Build ─────────────────────────────────────────────
        // Runs for BOTH PR and merge builds
        // PR build  → validates the Dockerfile compiles cleanly
        // Merge build → builds the final image for push
        stage('Docker Build') {
            steps {
                script {
                    if (env.CHANGE_ID) {
                        echo "🐳 PR Validation — building ${env.CHANGE_BRANCH} to verify Dockerfile..."
                    } else {
                        echo "🐳 Building final image: ${FULL_IMAGE}"
                    }
                }
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
                            echo "=== Verify Image ==="
                            docker images | grep ${IMAGE_NAME}
                            echo "✅ Docker build successful"
ENDSSH
                    """
                }
            }
        }

        // ── Stage 4: Cleanup PR Image ─────────────────────────────────────────
        // ONLY runs on PR builds — removes the test image, nothing gets pushed
        stage('Cleanup PR Image') {
            when {
                changeRequest()
            }
            steps {
                echo "🧹 PR build complete — removing local test image (not pushing to Nexus)"
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE -o StrictHostKeyChecking=no \\
                            ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'
                            echo "=== Removing PR test image ==="
                            docker rmi ${FULL_IMAGE} || true
                            echo "✅ Cleanup complete — PR validation done"
ENDSSH
                    """
                }
            }
        }

        // ── Stage 5: Docker Push to Nexus ─────────────────────────────────────
        // ONLY runs on merge builds (PR merged into dev)
        stage('Docker Push to Nexus') {
            when {
                not { changeRequest() }
            }
            steps {
                echo "📤 Pushing image to Nexus: ${FULL_IMAGE}"
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
                            echo "\$NEXUS_PASS" | docker login ${NEXUS_HOST} \\
                                --username "\$NEXUS_USER" --password-stdin

                            echo "=== Pushing Image ==="
                            docker push ${FULL_IMAGE}

                            echo "=== Logout ==="
                            docker logout ${NEXUS_HOST}

                            echo "=== Cleanup local image ==="
                            docker rmi ${FULL_IMAGE} || true

                            echo "✅ Successfully pushed: ${FULL_IMAGE}"
ENDSSH
                    """
                }
            }
        }

        // ── Stage 6: Verify Kubernetes Namespaces ─────────────────────────────
        // ONLY runs on merge builds
        stage('Verify Kubernetes') {
            when {
                not { changeRequest() }
            }
            steps {
                echo "☸️  Verifying Kubernetes namespaces..."
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

        // ── Stage 7: Deploy to EKS ────────────────────────────────────────────
        // ONLY runs on merge builds
        stage('Deploy to EKS') {
            when {
                not { changeRequest() }
            }
            steps {
                echo "☸️  Deploying to EKS namespace: ${K8S_NAMESPACE}"
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE -o StrictHostKeyChecking=no \\
                            ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'
                            echo "=== Pods Before Deploy ==="
                            kubectl get pods -n ${K8S_NAMESPACE} | grep ${K8S_DEPLOYMENT} || true

                            echo "=== Updating Image ==="
                            kubectl set image deployment/${K8S_DEPLOYMENT} \\
                                ${K8S_CONTAINER}=${FULL_IMAGE} \\
                                -n ${K8S_NAMESPACE}

                            echo "=== Waiting for Rollout ==="
                            kubectl rollout status deployment/${K8S_DEPLOYMENT} \\
                                -n ${K8S_NAMESPACE} --timeout=120s

                            echo "=== Pods After Deploy ==="
                            kubectl get pods -n ${K8S_NAMESPACE} | grep ${K8S_DEPLOYMENT}

                            echo "=== Running Image ==="
                            kubectl get deployment ${K8S_DEPLOYMENT} -n ${K8S_NAMESPACE} \\
                                -o=jsonpath='{.spec.template.spec.containers[0].image}'
                            echo ""
                            echo "✅ Deployment successful: ${FULL_IMAGE}"
ENDSSH
                    """
                }
            }
        }
    }

    // ── Post Actions ──────────────────────────────────────────────────────────
    post {
        success {
            script {
                if (env.CHANGE_ID) {
                    echo "========================================================"
                    echo "  ✅ PR VALIDATION PASSED"
                    echo "  PR       : #${env.CHANGE_ID} — ${env.CHANGE_TITLE}"
                    echo "  Branch   : ${env.CHANGE_BRANCH} → ${env.CHANGE_TARGET}"
                    echo "  Build    : #${BUILD_NUMBER}"
                    echo "  ✅ Dockerfile is valid — safe to merge into dev"
                    echo "========================================================"
                } else {
                    echo "========================================================"
                    echo "  ✅ DEPLOYMENT SUCCESSFUL"
                    echo "  Branch    : ${env.BRANCH_NAME}"
                    echo "  Image     : ${FULL_IMAGE}"
                    echo "  Namespace : ${K8S_NAMESPACE}"
                    echo "  Build     : #${BUILD_NUMBER}"
                    echo "========================================================"
                }
            }
        }
        failure {
            script {
                if (env.CHANGE_ID) {
                    echo "========================================================"
                    echo "  ❌ PR VALIDATION FAILED"
                    echo "  PR    : #${env.CHANGE_ID} — ${env.CHANGE_TITLE}"
                    echo "  Build : #${BUILD_NUMBER}"
                    echo "  ❌ Fix the errors above before merging!"
                    echo "========================================================"
                } else {
                    echo "========================================================"
                    echo "  ❌ DEPLOYMENT FAILED"
                    echo "  Branch : ${env.BRANCH_NAME}"
                    echo "  Build  : #${BUILD_NUMBER}"
                    echo "  ❌ Check console output above for details"
                    echo "========================================================"
                }
            }
        }
        always {
            echo "🧹 Cleaning Jenkins workspace..."
            cleanWs()
        }
    }
}
