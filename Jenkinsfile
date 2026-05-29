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

        REPO_URL       = "https://github.com/BuyMyVerse/product-service"

        // ── Teams Webhook — hardcoded ─────────────────────────────────────────
        TEAMS_URL      = "https://YOUR-TEAMS-WEBHOOK-URL-HERE"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        overrideIndexTriggers(true)
    }

    stages {

        // ── Stage 1: Collect Git Info ─────────────────────────────────────────
        stage('Collect Git Info') {
            steps {
                script {
                    env.SOURCE_BRANCH = env.CHANGE_ID ? env.CHANGE_BRANCH : env.BRANCH_NAME

                    env.COMMITTED_BY  = env.CHANGE_AUTHOR ?: sh(
                        script: "git log -1 --pretty=format:'%an' 2>/dev/null || echo 'unknown'",
                        returnStdout: true
                    ).trim()

                    env.COMMIT_MSG = sh(
                        script: "git log -1 --pretty=format:'%s' 2>/dev/null || echo 'N/A'",
                        returnStdout: true
                    ).trim().replaceAll("'", "").replaceAll('"', '')

                    env.PR_URL    = env.CHANGE_ID
                        ? "${REPO_URL}/pull/${env.CHANGE_ID}"
                        : "${REPO_URL}/tree/${env.BRANCH_NAME}"

                    env.JOB_SHORT = env.JOB_NAME.tokenize('/').last()

                    echo "=== Git Info ==="
                    echo "Source Branch : ${env.SOURCE_BRANCH}"
                    echo "Committed By  : ${env.COMMITTED_BY}"
                    echo "Commit Msg    : ${env.COMMIT_MSG}"
                    echo "PR URL        : ${env.PR_URL}"
                    echo "Image Tag     : ${IMAGE_TAG}"
                }
            }
        }

        // ── Stage 2: Build Notification ───────────────────────────────────────
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

                        // ── Teams: Deployment Started ──────────────────────────
                        sh """
                            curl -s -X POST "${TEAMS_URL}" \\
                            -H "Content-Type: application/json" \\
                            -d '{
                                "status": "started",
                                "job": "${env.JOB_SHORT}",
                                "environment": "DEV",
                                "branch": "${env.SOURCE_BRANCH}",
                                "committed_by": "${env.COMMITTED_BY}",
                                "commit_message": "${env.COMMIT_MSG}",
                                "pr_url": "${env.PR_URL}",
                                "image_tag": "${IMAGE_TAG}"
                            }'
                        """
                    }
                }
            }
        }

        // ── Stage 3: Git Checkout ─────────────────────────────────────────────
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

        // ── Stage 4: Docker Build ─────────────────────────────────────────────
        // Runs for BOTH PR and merge builds
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

        // ── Stage 5: Cleanup PR Image ─────────────────────────────────────────
        // ONLY runs on PR builds
        stage('Cleanup PR Image') {
            when { changeRequest() }
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

        // ── Stage 6: Docker Push to Nexus ─────────────────────────────────────
        // ONLY runs when PR is merged into dev
        stage('Docker Push to Nexus') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'dev'
                }
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

        // ── Stage 7: Verify Kubernetes ────────────────────────────────────────
        // ONLY runs when PR is merged into dev
        stage('Verify Kubernetes') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'dev'
                }
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

        // ── Stage 8: Deploy to EKS ────────────────────────────────────────────
        // ONLY runs when PR is merged into dev
        stage('Deploy to EKS') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'dev'
                }
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
                    // PR build — console only, no Teams
                    echo "========================================================"
                    echo "  ✅ PR VALIDATION PASSED"
                    echo "  PR       : #${env.CHANGE_ID} — ${env.CHANGE_TITLE}"
                    echo "  Branch   : ${env.CHANGE_BRANCH} → ${env.CHANGE_TARGET}"
                    echo "  Build    : #${BUILD_NUMBER}"
                    echo "  ✅ Dockerfile is valid — safe to merge into dev"
                    echo "========================================================"
                } else {
                    // Merge build — console + Teams success
                    echo "========================================================"
                    echo "  ✅ DEPLOYMENT SUCCESSFUL"
                    echo "  Branch    : ${env.BRANCH_NAME}"
                    echo "  Image     : ${FULL_IMAGE}"
                    echo "  Namespace : ${K8S_NAMESPACE}"
                    echo "  Build     : #${BUILD_NUMBER}"
                    echo "========================================================"

                    sh """
                        curl -s -X POST "${TEAMS_URL}" \\
                        -H "Content-Type: application/json" \\
                        -d '{
                            "status": "ended",
                            "job": "${env.JOB_SHORT}",
                            "environment": "DEV",
                            "branch": "${env.SOURCE_BRANCH}",
                            "committed_by": "${env.COMMITTED_BY}",
                            "commit_message": "${env.COMMIT_MSG}",
                            "pr_url": "${env.PR_URL}",
                            "image_tag": "${IMAGE_TAG}",
                            "docker_image": "${FULL_IMAGE}",
                            "result": "SUCCESS"
                        }'
                    """
                }
            }
        }
        failure {
            script {
                if (env.CHANGE_ID) {
                    // PR build — console only, no Teams
                    echo "========================================================"
                    echo "  ❌ PR VALIDATION FAILED"
                    echo "  PR    : #${env.CHANGE_ID} — ${env.CHANGE_TITLE}"
                    echo "  Build : #${BUILD_NUMBER}"
                    echo "  ❌ Fix the errors above before merging!"
                    echo "========================================================"
                } else {
                    // Merge build — console + Teams failure
                    echo "========================================================"
                    echo "  ❌ DEPLOYMENT FAILED"
                    echo "  Branch : ${env.BRANCH_NAME}"
                    echo "  Build  : #${BUILD_NUMBER}"
                    echo "  ❌ Check console output above for details"
                    echo "========================================================"

                    sh """
                        curl -s -X POST "${TEAMS_URL}" \\
                        -H "Content-Type: application/json" \\
                        -d '{
                            "status": "ended",
                            "job": "${env.JOB_SHORT}",
                            "environment": "DEV",
                            "branch": "${env.SOURCE_BRANCH}",
                            "committed_by": "${env.COMMITTED_BY}",
                            "commit_message": "${env.COMMIT_MSG}",
                            "pr_url": "${env.PR_URL}",
                            "image_tag": "${IMAGE_TAG}",
                            "result": "FAILED"
                        }'
                    """
                }
            }
        }
        always {
            script {
                try {
                    cleanWs()
                    echo "🧹 Workspace cleaned successfully"
                } catch (Exception e) {
                    echo "⚠️ Workspace cleanup skipped — no workspace context"
                }
            }
        }
    }
}
