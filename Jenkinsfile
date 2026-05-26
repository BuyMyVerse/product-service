pipeline {
    agent {
        kubernetes {
            inheritFrom 'jenkins-agent'
            defaultContainer 'jnlp' 
        }
    }
    environment {
        REMOTE_HOST = "3.226.177.66"
        REMOTE_USER = "admin"
        SSH_CRED_ID = "jenkins-agent-ssh-key"
        REMOTE_PATH = "/home/admin/Jenkins-deployment/product-service"
        GIT_BRANCH = "qa"
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 15, unit: 'MINUTES')
    }
    stages {
        stage('Checkout') {
            steps {
                echo "📥 Checking out source inside EKS pod..."
                checkout scm
            }
        }

        stage('SSH: List Home Directory') {
            steps {
                echo "🔐 Connecting from EKS pod → admin@${REMOTE_HOST}..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o BatchMode=yes \\
                            ${REMOTE_USER}@${REMOTE_HOST} '
                            echo "✅ SSH Connection Successful!"
                            ls -lah \$HOME
                        '
                    """
                }
            }
        }

        stage('CD: Navigate to Project Directory') {
            steps {
                echo "📂 Navigating to ${REMOTE_PATH} on remote host..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o BatchMode=yes \\
                            ${REMOTE_USER}@${REMOTE_HOST} '
                            cd ${REMOTE_PATH} || { echo "❌ Directory not found: ${REMOTE_PATH}"; exit 1; }
                            echo "✅ Current directory: \$(pwd)"
                            ls -lah
                        '
                    """
                }
            }
        }

        stage('Git: Checkout QA Branch') {
            steps {
                echo "🌿 Switching to branch ${GIT_BRANCH} on remote host..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o BatchMode=yes \\
                            ${REMOTE_USER}@${REMOTE_HOST} '
                            cd ${REMOTE_PATH} || { echo "❌ Directory not found: ${REMOTE_PATH}"; exit 1; }
                            echo "🔄 Fetching latest from remote..."
                            git fetch origin
                            echo "🌿 Checking out branch: ${GIT_BRANCH}"
                            git checkout ${GIT_BRANCH} || git checkout -b ${GIT_BRANCH} origin/${GIT_BRANCH}
                            echo "⬇️  Pulling latest changes..."
                            git pull origin ${GIT_BRANCH}
                            echo "✅ Current branch: \$(git branch --show-current)"
                            echo "📝 Latest commit: \$(git log -1 --oneline)"
                        '
                    """
                }
            }
        }

        stage('Build: Maven Clean Install') {
            steps {
                echo "🔨 Running mvn clean install on remote host..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o BatchMode=yes \\
                            ${REMOTE_USER}@${REMOTE_HOST} '
                            cd ${REMOTE_PATH} || { echo "❌ Directory not found: ${REMOTE_PATH}"; exit 1; }
                            echo "🔨 Starting Maven build..."
                            mvn clean install -DskipTests=false
                            echo "✅ Maven build completed successfully!"
                        '
                    """
                }
            }
        }

        stage('Deploy: Maven Clean Deploy') {
            steps {
                echo "🚀 Running mvn clean deploy on remote host..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o BatchMode=yes \\
                            ${REMOTE_USER}@${REMOTE_HOST} '
                            cd ${REMOTE_PATH} || { echo "❌ Directory not found: ${REMOTE_PATH}"; exit 1; }
                            echo "🚀 Starting Maven deploy..."
                            mvn clean deploy
                            echo "✅ Maven deploy completed successfully!"
                        '
                    """
                }
            }
        }
    }

    post {
        success { echo "✅ Pipeline completed. EKS pod cleaned up." }
        failure  { echo "❌ Pipeline failed. EKS pod still cleaned up." }
        always   { cleanWs() }
    }
}
