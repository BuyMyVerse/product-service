pipeline {
    agent any

    environment {
        REMOTE_HOST  = "3.226.177.66"
        REMOTE_USER  = "admin"
        SSH_CRED_ID  = "jenkins-agent-ssh-key"   // ← Jenkins credential ID (SSH private key)
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 10, unit: 'MINUTES')
    }

    stages {

        // ── 1. Checkout ──────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                echo "📥 Checking out source..."
                checkout scm
            }
        }

        // ── 2. SSH into server and list home directory ───────────────────────
        // Uses withCredentials (no SSH Agent plugin needed)
        stage('SSH: List Home Directory') {
            steps {
                echo "🔐 Connecting to ${REMOTE_USER}@${REMOTE_HOST}..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE

                        ssh -i \$SSH_KEY_FILE \
                            -o StrictHostKeyChecking=no \
                            -o BatchMode=yes \
                            ${REMOTE_USER}@${REMOTE_HOST} '

                            echo "======================================"
                            echo "  SSH Connection Successful!"
                            echo "======================================"
                            echo "User     : \$(whoami)"
                            echo "Hostname : \$(hostname)"
                            echo "Home Dir : \$HOME"
                            echo ""
                            echo "Home Directory Contents:"
                            echo "--------------------------------------"
                            ls -lah \$HOME
                            echo "--------------------------------------"
                        '
                    """
                }
            }
        }
    }

    post {
        success { echo "Pipeline completed successfully." }
        failure  { echo "Pipeline failed. Check console output above." }
        always   { cleanWs() }
    }
}
