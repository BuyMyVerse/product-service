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
            description: '''Select build type:
  QA      → downloads 1.0.0-SNAPSHOT from buymyverse-maven-dev
  Beta    → downloads 1.0.0-BETA     from buymyverse-maven-beta
  Release → downloads 1.0.0          from buymyverse-maven-prod'''
        )
        string(
            name: 'VERSION',
            defaultValue: '1.0.0',
            description: '''Base version number only — DO NOT add suffixes.
  ✅ Correct : 1.0.0
  ❌ Wrong   : 1.0.0-SNAPSHOT  or  1.0.0-BETA'''
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
                        error("❌ VERSION must follow semver: MAJOR.MINOR.PATCH (e.g. 1.0.0) — DO NOT add -SNAPSHOT or -BETA suffix")
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
                    ║  NEXUS_URL  : ${env.NEXUS_ARTIFACT_URL}
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
        // STAGE 2 — Clean & Prepare Demo Environment Directory
        // ─────────────────────────────────────────────────────────────────
        stage('Prepare: Clean & Create Demo Directory') {
            steps {
                echo "🗑️  Cleaning and preparing ${DEMO_PATH}..."
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
                                echo "🗑️  Wiping Demo Environment folder..."
                                rm -rf ${DEMO_PATH}
                                mkdir -p ${DEMO_PATH}
                                echo "✅ Folder cleaned and recreated: ${DEMO_PATH}"

                                echo ""
                                echo "🗑️  Wiping .m2 cache for ${ARTIFACT_ID}..."
                                rm -rf \$HOME/.m2/repository/com/buymyverse/${ARTIFACT_ID}/
                                echo "✅ .m2 cache cleared"

                                echo ""
                                echo "📂 Demo Environment ready:"
                                ls -lah ${DEMO_PATH}
                            '
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 3 — Download All Artifact Files from Nexus
        // Downloads: .jar .jar.md5 .jar.sha1 .pom .pom.md5 .pom.sha1
        // QA/SNAPSHOT  → parse maven-metadata.xml → get latest timestamp
        // Beta/Release → direct curl (exact filename known)
        // ─────────────────────────────────────────────────────────────────
        stage('Download: Pull All Artifact Files from Nexus') {
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
                            DEMO_PATH="${DEMO_PATH}"
                            ARTIFACT_ID="${ARTIFACT_ID}"
                            MVN_VERSION="${env.MVN_VERSION}"

                            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                            echo "⬇️  Artifact URL : \${NEXUS_ARTIFACT_URL}"
                            echo "📦 Version      : \${MVN_VERSION}"
                            echo "📂 Destination  : \${DEMO_PATH}"
                            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

                            # ── Resolve base filename ──────────────────────────────────
                            if [ "\${IS_SNAPSHOT}" = "true" ]; then
                                echo ""
                                echo "🔍 SNAPSHOT — resolving latest timestamp from metadata..."
                                METADATA_URL="\${NEXUS_ARTIFACT_URL}/maven-metadata.xml"
                                echo "📄 Fetching: \${METADATA_URL}"

                                METADATA=\$(curl -sf -u "\${NEXUS_USER}:\${NEXUS_PASS}" "\${METADATA_URL}")

                                if [ -z "\${METADATA}" ]; then
                                    echo "❌ Failed to fetch maven-metadata.xml from Nexus"
                                    exit 1
                                fi

                                echo "\${METADATA}"

                                # Extract latest timestamped version e.g. 1.0.0-20260526.183249-6
                                SNAPSHOT_VERSION=\$(echo "\${METADATA}" | grep -o '<value>[^<]*</value>' | tail -1 | sed 's/<[^>]*>//g')

                                if [ -z "\${SNAPSHOT_VERSION}" ]; then
                                    echo "❌ Could not resolve snapshot version from metadata"
                                    exit 1
                                fi

                                echo "✅ Latest snapshot version: \${SNAPSHOT_VERSION}"
                                BASE_FILENAME="\${ARTIFACT_ID}-\${SNAPSHOT_VERSION}"
                            else
                                BASE_FILENAME="\${ARTIFACT_ID}-\${MVN_VERSION}"
                            fi

                            echo ""
                            echo "📦 Base filename resolved: \${BASE_FILENAME}"
                            echo ""
                            echo "⬇️  Downloading all artifact files..."
                            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

                            # ── Download all 6 files ───────────────────────────────────
                            for EXT in jar jar.md5 jar.sha1 pom pom.md5 pom.sha1; do

                                SRC_FILE="\${BASE_FILENAME}.\${EXT}"
                                DOWNLOAD_URL="\${NEXUS_ARTIFACT_URL}/\${SRC_FILE}"

                                # For SNAPSHOT: save with clean MVN_VERSION name
                                if [ "\${IS_SNAPSHOT}" = "true" ]; then
                                    DEST_FILE="\${ARTIFACT_ID}-\${MVN_VERSION}.\${EXT}"
                                else
                                    DEST_FILE="\${SRC_FILE}"
                                fi

                                echo "⬇️  Downloading : \${SRC_FILE}"
                                echo "    → Saving as : \${DEST_FILE}"

                                curl -f \\
                                    --progress-bar \\
                                    -u "\${NEXUS_USER}:\${NEXUS_PASS}" \\
                                    -o "\${DEMO_PATH}/\${DEST_FILE}" \\
                                    "\${DOWNLOAD_URL}" \\
                                && echo "✅ Done     : \${DEST_FILE}" \\
                                || echo "⚠️  Skipped  : \${SRC_FILE} (not found in Nexus)"

                                echo ""
                            done

                            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                            echo "✅ All files downloaded!"
                            echo ""
                            echo "📂 Demo Environment contents:"
                            ls -lah \${DEMO_PATH}

ENDSSH
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 4 — Verify All Files in Demo Environment
        // ─────────────────────────────────────────────────────────────────
        stage('Verify: Artifact in Demo Environment') {
            steps {
                echo "🔍 Verifying all artifact files in ${DEMO_PATH}..."
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
                                echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                                echo "📂 Demo Environment: ${DEMO_PATH}"
                                echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                                ls -lah ${DEMO_PATH}
                                echo ""

                                # ── Verify JAR ─────────────────────────────────────────
                                JAR_FILE=\$(find ${DEMO_PATH} -name "*.jar" ! -name "*sources*" ! -name "*javadoc*" | head -1)
                                if [ -f "\${JAR_FILE}" ]; then
                                    echo "✅ JAR     : \${JAR_FILE}"
                                    echo "   Size    : \$(du -sh \${JAR_FILE} | cut -f1)"
                                    echo "   Modified: \$(stat -c %y \${JAR_FILE})"
                                else
                                    echo "❌ JAR not found in ${DEMO_PATH}"
                                    exit 1
                                fi

                                echo ""

                                # ── Verify POM ─────────────────────────────────────────
                                POM_FILE=\$(find ${DEMO_PATH} -name "*.pom" | head -1)
                                if [ -f "\${POM_FILE}" ]; then
                                    echo "✅ POM     : \${POM_FILE}"
                                    echo "   Size    : \$(du -sh \${POM_FILE} | cut -f1)"
                                else
                                    echo "❌ POM not found in ${DEMO_PATH}"
                                    exit 1
                                fi

                                echo ""

                                # ── Verify Checksums ───────────────────────────────────
                                MD5_COUNT=\$(find ${DEMO_PATH} -name "*.md5" | wc -l)
                                SHA1_COUNT=\$(find ${DEMO_PATH} -name "*.sha1" | wc -l)
                                echo "✅ MD5  files : \${MD5_COUNT}"
                                echo "✅ SHA1 files : \${SHA1_COUNT}"

                                echo ""
                                echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                                echo "✅ All artifact files verified successfully!"
                                echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
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
            ║  FILES   : jar, jar.md5, jar.sha1
            ║            pom, pom.md5, pom.sha1
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
