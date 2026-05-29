```groovy
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

        TEAMS_WEBHOOK  = "https://your-webhook-url"
        REPO_URL       = "https://github.com/BuyMyVerse/product-service"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {

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

                    } else {

                        echo "========================================================"
                        echo "  🚀 FULL DEPLOYMENT BUILD"
                        echo "========================================================"
                        echo "  Job        : ${JOB_NAME}"
                        echo "  Build No   : #${BUILD_NUMBER}"
                        echo "  Branch     : ${env.BRANCH_NAME}"
                        echo "  Image Tag  : ${IMAGE_TAG}"
                        echo "  Full Image : ${FULL_IMAGE}"
                        echo "========================================================"
                    }
                }
            }
        }

        stage('Deployment Started Notification') {
            when {
                not { changeRequest() }
            }

            steps {

                echo "📣 Sending deployment started notification..."

                sh """
                    curl -s -X POST "${TEAMS_WEBHOOK}" \
                    -H "Content-Type: application/json" \
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

        stage('Git Checkout') {
            steps {

                script {

                    def targetBranch = env.CHANGE_ID ?
                        env.CHANGE_BRANCH :
                        env.BRANCH_NAME

                    echo "📥 Checking out branch: ${targetBranch}"

                    withCredentials([
                        sshUserPrivateKey(
                            credentialsId: "${SSH_CRED_ID}",
                            keyFileVariable: 'SSH_KEY_FILE',
                            usernameVariable: 'SSH_USER'
                        )
                    ]) {

                        sh """
                            chmod 600 \$SSH_KEY_FILE

                            ssh -i \$SSH_KEY_FILE \
                            -o StrictHostKeyChecking=no \
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

        stage('Docker Build') {
            steps {

                script {

                    if (env.CHANGE_ID) {
                        echo "🐳 PR Validation Docker Build..."
                    } else {
                        echo "🐳 Building final image: ${FULL_IMAGE}"
                    }
                }

                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: "${SSH_CRED_ID}",
                        keyFileVariable: 'SSH_KEY_FILE',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {

                    sh """
                        chmod 600 \$SSH_KEY_FILE

                        ssh -i \$SSH_KEY_FILE \
                        -o StrictHostKeyChecking=no \
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

        stage('Cleanup PR Image') {
            when {
                changeRequest()
            }

            steps {

                echo "🧹 Removing PR image..."

                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: "${SSH_CRED_ID}",
                        keyFileVariable: 'SSH_KEY_FILE',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {

                    sh """
                        chmod 600 \$SSH_KEY_FILE

                        ssh -i \$SSH_KEY_FILE \
                        -o StrictHostKeyChecking=no \
                        ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'

                        docker rmi ${FULL_IMAGE} || true

                        echo "✅ Cleanup completed"

ENDSSH
                    """
                }
            }
        }

        stage('Docker Push to Nexus') {
            when {
                not { changeRequest() }
            }

            steps {

                echo "📤 Pushing image to Nexus..."

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

                        ssh -i \$SSH_KEY_FILE \
                        -o StrictHostKeyChecking=no \
                        ${REMOTE_USER}@${REMOTE_HOST} bash -e << ENDSSH

                        echo "=== Nexus Login ==="

                        echo "\$NEXUS_PASS" | docker login ${NEXUS_HOST} \
                        --username "\$NEXUS_USER" \
                        --password-stdin

                        echo "=== Push Image ==="

                        docker push ${FULL_IMAGE}

                        echo "=== Cleanup ==="

                        docker rmi ${FULL_IMAGE} || true

                        docker logout ${NEXUS_HOST}

                        echo "✅ Push successful"

ENDSSH
                    """
                }
            }
        }

        stage('Verify Kubernetes') {
            when {
                not { changeRequest() }
            }

            steps {

                echo "☸️ Verifying Kubernetes..."

                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: "${SSH_CRED_ID}",
                        keyFileVariable: 'SSH_KEY_FILE',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {

                    sh """
                        chmod 600 \$SSH_KEY_FILE

                        ssh -i \$SSH_KEY_FILE \
                        -o StrictHostKeyChecking=no \
                        ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'

                        kubectl get ns

ENDSSH
                    """
                }
            }
        }

        stage('Deploy to EKS') {
            when {
                not { changeRequest() }
            }

            steps {

                echo "☸️ Deploying to EKS..."

                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: "${SSH_CRED_ID}",
                        keyFileVariable: 'SSH_KEY_FILE',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {

                    sh """
                        chmod 600 \$SSH_KEY_FILE

                        ssh -i \$SSH_KEY_FILE \
                        -o StrictHostKeyChecking=no \
                        ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'

                        echo "=== Before Deploy ==="

                        kubectl get pods -n ${K8S_NAMESPACE} | grep ${K8S_DEPLOYMENT} || true

                        echo "=== Update Image ==="

                        kubectl set image deployment/${K8S_DEPLOYMENT} \
                        ${K8S_CONTAINER}=${FULL_IMAGE} \
                        -n ${K8S_NAMESPACE}

                        echo "=== Rollout Status ==="

                        kubectl rollout status deployment/${K8S_DEPLOYMENT} \
                        -n ${K8S_NAMESPACE} --timeout=120s

                        echo "=== Running Pods ==="

                        kubectl get pods -n ${K8S_NAMESPACE} | grep ${K8S_DEPLOYMENT}

                        echo "✅ Deployment successful"

ENDSSH
                    """
                }
            }
        }
    }

    post {

        success {

            script {

                if (env.CHANGE_ID) {

                    echo "========================================================"
                    echo "  ✅ PR VALIDATION PASSED"
                    echo "========================================================"

                } else {

                    sh """
                        curl -s -X POST "${TEAMS_WEBHOOK}" \
                        -H "Content-Type: application/json" \
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

                    echo "========================================================"
                    echo "  ✅ DEPLOYMENT SUCCESSFUL"
                    echo "========================================================"
                }
            }
        }

        failure {

            script {

                if (env.CHANGE_ID) {

                    echo "========================================================"
                    echo "  ❌ PR VALIDATION FAILED"
                    echo "========================================================"

                } else {

                    sh """
                        curl -s -X POST "${TEAMS_WEBHOOK}" \
                        -H "Content-Type: application/json" \
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

                    echo "========================================================"
                    echo "  ❌ DEPLOYMENT FAILED"
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
```
