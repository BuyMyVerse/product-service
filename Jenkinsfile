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

        // ── Teams Power Automate Webhook ──────────────────────────────────────
        TEAMS_WEBHOOK  = "https://defaulte3ce5830f7d140c0ab827ce4f99738.f0.environment.api.powerplatform.com:443/powerautomate/automations/direct/workflows/0417e5b8a7a747fd921d012dab200011/triggers/manual/paths/invoke?api-version=1&sp=%2Ftriggers%2Fmanual%2Frun&sv=1.0&sig=7UOHRrxDqoa8tcaV9n9-nQamG4hLXqSyJDzPx2zSbs8"
        REPO_URL       = "https://github.com/BuyMyVerse/product-service"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {

        // ── Stage 1: Prepare Metadata ─────────────────────────────────────────
        // Collects git info used in Teams notifications
        stage('Prepare Metadata') {
            steps {
                script {
                    env.COMMITTED_BY = sh(
                        script: 'git log -1 --pretty=format:"%an"',
                        returnStdout: true
                    ).trim()

                    env.COMMIT_MSG = sh(
                        script: 'git log -1 --pretty=format:"%s"',
                        returnStdout: true
                    ).trim()

                    env.SOURCE_BRANCH = sh(
                        script: '''
                            git log -1 --merges --pretty=format:"%s" | \
                            grep -oP "Merge pull request #\\d+ from \\K\\S+" || \
                            echo "${BRANCH_NAME}"
                        ''',
                        returnStdout: true
                    ).trim()

                    env.JOB_SHORT = env.JOB_NAME.tokenize('/').size() > 1 ?
                        env.JOB_NAME.tokenize('/')[1] :
                        env.JOB_NAME

                    env.PR_NUMBER = sh(
                        script: '''
                            git log -1 --pretty=format:"%s" | \
                            grep -oP "(?:Merge pull request #|\\(#)\\K\\d+" | head -1 || \
                            git log --merges --pretty=format:"%s" -10 | \
                            grep -oP "Merge pull request #\\K\\d+" | head -1 || \
                            echo ""
                        ''',
                        returnStdout: true
                    ).trim()

                    def prNum = env.PR_NUMBER?.trim()
                    if (env.CHANGE_URL) {
                        env.PR_URL = env.CHANGE_URL
                    } else if (prNum && prNum != '' && prNum != 'null') {
                        env.PR_URL = "${REPO_URL}/pull/${prNum}"
                    } else {
                        env.PR_URL = "${REPO_URL}/tree/${env.BRANCH_NAME}"
                    }

                    echo "============================================="
                    echo "COMMITTED_BY  : ${env.COMMITTED_BY}"
                    echo "SOURCE_BRANCH : ${env.SOURCE_BRANCH}"
                    echo "COMMIT_MSG    : ${env.COMMIT_MSG}"
                    echo "PR_NUMBER     : ${env.PR_NUMBER}"
                    echo "PR_URL        : ${env.PR_URL}"
                    echo "IMAGE_TAG     : ${IMAGE_TAG}"
                    echo "============================================="
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
                    }
                }
            }
        }

        // ── Stage 3: Teams Deployment Started Notification ────────────────────
        // ONLY runs on merge builds — notifies Teams that deployment has begun
        stage('Deployment Started Notification') {
            when {
                not { changeRequest() }
            }
            steps {
                echo "📣 Sending deployment started notification to Teams..."
                sh """
                    curl -s -X POST "${TEAMS_WEBHOOK}" \\
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

        // ── Stage 4: Git Checkout ─────────────────────────────────────────────
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

        // ── Stage 5: Docker Build ─────────────────────────────────────────────
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

        // ── Stage 6: Cleanup PR Image ─────────────────────────────────────────
        // ONLY runs on PR builds
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

        // ── Stage 7: Docker Push to Nexus ─────────────────────────────────────
        // ONLY runs on merge builds
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

        // ── Stage 8: Verify Kubernetes ────────────────────────────────────────
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

        // ── Stage 9: Deploy to EKS ────────────────────────────────────────────
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
                    // ── Teams: Deployment ended SUCCESS ───────────────────────
                    node {
                        sh """
                            curl -s -X POST "${TEAMS_WEBHOOK}" \\
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
                    // ── Teams: Deployment ended FAILED ────────────────────────
                    node {
                        sh """
                            curl -s -X POST "${TEAMS_WEBHOOK}" \\
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
            node {
                cleanWs()
            }
        }
    }
}
