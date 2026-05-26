pipeline {
    agent {
        kubernetes {
            inheritFrom 'jenkins-agent'
            defaultContainer 'jnlp'
        }
    }

    parameters {
        choice(
            name: 'LABEL',
            choices: ['QA', 'Beta', 'Release'],
            description: 'Select build type to download from Nexus'
        )
        string(
            name: 'VERSION',
            defaultValue: '',
            description: 'Enter the version to download (Example: 1.0.0)'
        )
    }

    environment {
        REMOTE_HOST    = "3.226.177.66"
        REMOTE_USER    = "admin"
        SSH_CRED_ID    = "jenkins-agent-ssh-key"
        NEXUS_BASE_URL = "https://dev-artifacthub.evaequitymtest.com/repository"
        DEMO_PATH      = "/home/admin/Jenkins-deployment/Demo-Environment"
        GROUP_PATH     = "com/buymyverse"
        ARTIFACT_ID    = "product-service"

        // ── Nexus Credentials ──────────────────────────────────
        NEXUS_USER     = "admin"
        NEXUS_PASS     = "nexusadmin"
        NEXUS_REGISTRY = "dev-artifacthub.evaequitymtest.com"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 10, unit: 'MINUTES')
    }

    stages {

        // ─────────────────────────────────────────────────────────────────
        // STAGE 0 — Validate & Resolve Parameters
        // ─────────────────────────────────────────────────────────────────
        stage('Validate Parameters') {
            steps {
                script {
                    if (!params.VERSION?.trim()) {
                        error("❌ VERSION is required! Example: 1.0.0")
                    }
                    if (!(params.VERSION ==~ /^\d+\.\d+\.\d+$/)) {
                        error("❌ VERSION must follow semver: MAJOR.MINOR.PATCH (e.g. 1.0.0)")
                    }

                    switch (params.LABEL) {
                        case 'QA':
                            env.NEXUS_REPO  = 'buymyverse-maven-dev'
                            env.MVN_VERSION = "${params.VERSION}-SNAPSHOT"
                            env.IS_SNAPSHOT = 'true'
                            break

                        case 'Beta':
                            env.NEXUS_REPO  = 'buymyverse-maven-beta'
                            env.MVN_VERSION = "${params.VERSION}-BETA"
                            env.IS_SNAPSHOT = 'false'
                            break

                        case 'Release':
                            env.NEXUS_REPO  = 'buymyverse-maven-prod'
                            env.MVN_VERSION = "${params.VERSION}"
                            env.IS_SNAPSHOT = 'false'
                            break

                        default:
                            error("❌ Unknown LABEL: ${params.LABEL}")
                    }

                    env.NEXUS_ARTIFACT_URL = "${NEXUS_BASE_URL}/${env.NEXUS_REPO}/${GROUP_PATH}/${ARTIFACT_ID}/${env.MVN_VERSION}"
                    env.DEST_JAR_NAME      = "${ARTIFACT_ID}-${env.MVN_VERSION}.jar"

                    echo """
                    ╔══════════════════════════════════════════╗
                    ║       DOWNLOAD PARAMETERS RESOLVED       ║
                    ╠══════════════════════════════════════════╣
                    ║  LABEL      : ${params.LABEL}
                    ║  VERSION    : ${params.VERSION}
                    ║  MVN_VERSION: ${env.MVN_VERSION}
                    ║  NEXUS_REPO : ${env.NEXUS_REPO}
                    ║  IS_SNAPSHOT: ${env.IS_SNAPSHOT}
                    ║  ARTIFACT   : ${env.DEST_JAR_NAME}
                    ║  DEST_PATH  : ${DEMO_PATH}
                    ╚══════════════════════════════════════════╝
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 1 — SSH Verify Connection
        // ─────────────────────────────────────────────────────────────────
        stage('SSH: Verify Connection') {
            steps {
                echo "🔐 Connecting EKS pod → ${REMOTE_USER}@${REMOTE_HOST}..."
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
                                echo "🖥️  Host: \$(hostname)"
                                echo "📅 Date: \$(date)"
                            '
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 2 — Prepare Demo Environment Directory
        // ─────────────────────────────────────────────────────────────────
        stage('Prepare: Demo Environment Directory') {
            steps {
                echo "📂 Preparing ${DEMO_PATH}..."
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
                                mkdir -p ${DEMO_PATH}
                                echo "✅ Directory ready: ${DEMO_PATH}"
                                echo "📂 Current contents:"
                                ls -lah ${DEMO_PATH}
                            '
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 3 — Download Artifact from Nexus
        // Beta/Release → direct curl (exact filename known)
        // QA/SNAPSHOT  → parse maven-metadata.xml → get latest timestamp
        // ─────────────────────────────────────────────────────────────────
        stage('Download: Pull Artifact from Nexus') {
            steps {
                echo "⬇️  Downloading ${env.MVN_VERSION} from ${env.NEXUS_REPO}..."
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
                            ${REMOTE_USER}@${REMOTE_HOST} bash -s << 'ENDSSH'

                            set -e

                            NEXUS_USER="${NEXUS_USER}"
                            NEXUS_PASS="${NEXUS_PASS}"
                            NEXUS_ARTIFACT_URL="${env.NEXUS_ARTIFACT_URL}"
                            IS_SNAPSHOT="${env.IS_SNAPSHOT}"
                            DEST_JAR_NAME="${env.DEST_JAR_NAME}"
                            DEMO_PATH="${DEMO_PATH}"
                            ARTIFACT_ID="${ARTIFACT_ID}"
                            MVN_VERSION="${env.MVN_VERSION}"

                            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                            echo "⬇️  Artifact URL base : \${NEXUS_ARTIFACT_URL}"
                            echo "📦 Target jar        : \${DEST_JAR_NAME}"
                            echo "📂 Destination       : \${DEMO_PATH}"
                            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

                            if [ "\${IS_SNAPSHOT}" = "true" ]; then

                                # ── SNAPSHOT: resolve latest timestamped jar via metadata ──
                                echo "🔍 SNAPSHOT detected — resolving latest timestamp..."
                                METADATA_URL="\${NEXUS_ARTIFACT_URL}/maven-metadata.xml"
                                echo "📄 Fetching metadata: \${METADATA_URL}"

                                METADATA=\$(curl -sf -u "\${NEXUS_USER}:\${NEXUS_PASS}" "\${METADATA_URL}")

                                if [ -z "\${METADATA}" ]; then
                                    echo "❌ Failed to fetch maven-metadata.xml from Nexus"
                                    exit 1
                                fi

                                echo "\${METADATA}"

                                # Extract latest snapshot value (e.g. 1.0.0-20260526.183249-6)
                                SNAPSHOT_VERSION=\$(echo "\${METADATA}" | grep -o '<value>[^<]*</value>' | tail -1 | sed 's/<[^>]*>//g')

                                if [ -z "\${SNAPSHOT_VERSION}" ]; then
                                    echo "❌ Could not resolve snapshot version from metadata"
                                    exit 1
                                fi

                                echo "✅ Latest snapshot  : \${SNAPSHOT_VERSION}"
                                JAR_FILENAME="\${ARTIFACT_ID}-\${SNAPSHOT_VERSION}.jar"
                                DOWNLOAD_URL="\${NEXUS_ARTIFACT_URL}/\${JAR_FILENAME}"

                            else

                                # ── Beta / Release: exact filename directly ─────────────
                                JAR_FILENAME="\${DEST_JAR_NAME}"
                                DOWNLOAD_URL="\${NEXUS_ARTIFACT_URL}/\${JAR_FILENAME}"

                            fi

                            echo ""
                            echo "🌐 Download URL : \${DOWNLOAD_URL}"
                            echo "💾 Save as      : \${DEMO_PATH}/\${DEST_JAR_NAME}"
                            echo ""

                            # ── Clean old jars from demo folder ────────────────────────
                            echo "🧹 Cleaning old \${ARTIFACT_ID} jars from \${DEMO_PATH}..."
                            rm -f \${DEMO_PATH}/\${ARTIFACT_ID}-*.jar
                            echo "✅ Old jars removed"

                            # ── Download from Nexus ────────────────────────────────────
                            echo "⬇️  Downloading now..."
                            curl -f \\
                                --progress-bar \\
                                -u "\${NEXUS_USER}:\${NEXUS_PASS}" \\
                                -o "\${DEMO_PATH}/\${DEST_JAR_NAME}" \\
                                "\${DOWNLOAD_URL}"

                            echo ""
                            echo "✅ Download complete!"
                            echo ""
                            echo "📂 Demo Environment contents:"
                            ls -lah \${DEMO_PATH}

ENDSSH
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 4 — Verify Artifact in Demo Environment
        // ─────────────────────────────────────────────────────────────────
        stage('Verify: Artifact in Demo Environment') {
            steps {
                echo "🔍 Verifying artifact landed correctly..."
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
                                DEST="${DEMO_PATH}/${env.DEST_JAR_NAME}"

                                if [ -f "\${DEST}" ]; then
                                    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                                    echo "✅ Artifact verified!"
                                    echo "📦 File    : \${DEST}"
                                    echo "📏 Size    : \$(du -sh \${DEST} | cut -f1)"
                                    echo "🕐 Modified: \$(stat -c %y \${DEST})"
                                    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                                else
                                    echo "❌ Artifact NOT found at: \${DEST}"
                                    exit 1
                                fi

                                echo ""
                                echo "📂 Full Demo Environment:"
                                ls -lah ${DEMO_PATH}
                            '
                    """
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST
    // ─────────────────────────────────────────────────────────────────────
    post {
        success {
            echo """
            ╔══════════════════════════════════════════╗
            ║         ✅ DOWNLOAD SUCCESS               ║
            ╠══════════════════════════════════════════╣
            ║  LABEL   : ${params.LABEL}
            ║  VERSION : ${env.MVN_VERSION}
            ║  REPO    : ${env.NEXUS_REPO}
            ║  JAR     : ${env.DEST_JAR_NAME}
            ║  DEST    : ${DEMO_PATH}
            ╚══════════════════════════════════════════╝
            """
        }
        failure {
            echo """
            ╔══════════════════════════════════════════╗
            ║         ❌ DOWNLOAD FAILED                ║
            ╠══════════════════════════════════════════╣
            ║  LABEL   : ${params.LABEL}
            ║  VERSION : ${params.VERSION}
            ║  Check logs above for details.
            ╚══════════════════════════════════════════╝
            """
        }
        always {
            cleanWs()
        }
    }
}
