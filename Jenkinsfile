pipeline {
    agent any

    environment {
        AWS_REGION      = 'us-east-1'
        ECR_REGISTRY    = '909783398453.dkr.ecr.us-east-1.amazonaws.com'
        ECR_REPO        = 'buymyverse/product-service'
        IMAGE_TAG       = "build-${env.BUILD_NUMBER}"
        
        AWS_ACCESS_KEY  = credentials('aws-access-key-id')
        AWS_SECRET_KEY  = credentials('aws-secret-access-key')
        TEAMS_URL       = credentials('jenkins-cicd-webhook-url')

        REPO_URL        = 'https://github.com/BuyMyVerse/product-service'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timestamps()
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
                        script: 'git log -1 --merges --pretty=format:"%s" | grep -oP "Merge pull request #\\d+ from \\K\\S+" || echo "${BRANCH_NAME}"',
                        returnStdout: true
                    ).trim()

                    env.JOB_SHORT = env.JOB_NAME.tokenize('/').size() > 1 ?
                        env.JOB_NAME.tokenize('/')[1] :
                        env.JOB_NAME

                    env.PR_NUMBER = sh(
                        script: '''
                            git log -1 --pretty=format:"%s" | grep -oP "(?:Merge pull request #|\\(#)\\K\\d+" | head -1 || \
                            git log --merges --pretty=format:"%s" -10 | grep -oP "Merge pull request #\\K\\d+" | head -1 || \
                            echo ""
                        ''',
                        returnStdout: true
                    ).trim()

                    env.COMMIT_HASH = sh(
                        script: 'git log -1 --pretty=format:"%H"',
                        returnStdout: true
                    ).trim()

                    def prNum = env.PR_NUMBER?.trim()
                    if (env.CHANGE_URL) {
                        env.PR_URL = env.CHANGE_URL
                    } else if (prNum && prNum != '' && prNum != 'null') {
                        env.PR_URL = "${env.REPO_URL}/pull/${prNum}"
                    } else {
                        env.PR_URL = "${env.REPO_URL}/tree/${env.BRANCH_NAME}"
                    }

                    env.ACTUAL_BRANCH = env.CHANGE_BRANCH ?: env.BRANCH_NAME

                    echo "============================================="
                    echo "COMMITTED_BY  : ${env.COMMITTED_BY}"
                    echo "SOURCE_BRANCH : ${env.SOURCE_BRANCH}"
                    echo "ACTUAL_BRANCH : ${env.ACTUAL_BRANCH}"
                    echo "COMMIT_MSG    : ${env.COMMIT_MSG}"
                    echo "PR_NUMBER     : ${env.PR_NUMBER}"
                    echo "COMMIT_HASH   : ${env.COMMIT_HASH}"
                    echo "PR_URL        : ${env.PR_URL}"
                    echo "=============================================="
                }
            }
        }

        stage('Build Maven Project') {
            steps {
                echo 'Building Maven Project...'
                script{
                    def mvnHome = tool name: 'Maven-3.9', type: 'maven'
                    sh "${mvnHome}/bin/mvn clean install -DskipTests"
                }
            }
        }

        stage('Docker Build') {
            steps {
                echo 'Building Docker Image....'
                sh """
                    docker build -t ${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG} .
                    docker tag ${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_REPO}:latest
                """
            }
        }

        stage('Push to ECR') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'dev'
                }
            }
            steps {
                echo 'Pushing Docker Image to ECR...'
                sh """
                    export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY}
                    export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_KEY}
                    aws ecr get-login-password --region ${AWS_REGION} | \
                    docker login --username AWS --password-stdin ${ECR_REGISTRY}
                    docker push ${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}
                    docker push ${ECR_REGISTRY}/${ECR_REPO}:latest
                """

                script {
                    env.DOCKER_IMAGE = "${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}"
                }
            }
        }

        stage('Deployment Notification') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'dev'
                }
            }
            steps {
                echo 'Sending Deployment Started Notification...'
                sh """
                    curl -s -X POST "${TEAMS_URL}" \\
                    -H "Content-Type: application/json" \\
                    -d '{
                        "status": "started",
                        "job": "${env.JOB_SHORT}",
                        "environment": "Development",
                        "branch": "${env.SOURCE_BRANCH}",
                        "committed_by": "${env.COMMITTED_BY}",
                        "commit_message": "${env.COMMIT_MSG}",
                        "pr_url": "${env.PR_URL}"
                    }'
                """
            }
        }
    }

    post {
        success {
            script {
                if (env.BRANCH_NAME == 'dev' && !env.CHANGE_ID) {
                    sh """
                        curl -s -X POST "${TEAMS_URL}" \\
                        -H "Content-Type: application/json" \\
                        -d '{
                            "status": "ended",
                            "job": "${env.JOB_SHORT}",
                            "environment": "Development",
                            "branch": "${env.SOURCE_BRANCH}",
                            "committed_by": "${env.COMMITTED_BY}",
                            "commit_message": "${env.COMMIT_MSG}",
                            "pr_url": "${env.PR_URL}",
                            "docker_image": "${env.DOCKER_IMAGE}",
                            "result": "SUCCESS"
                        }'
                    """
                }
            }
            echo 'Pipeline completed successfully!'
        }

        failure {
            script {
                if (env.BRANCH_NAME == 'dev' && !env.CHANGE_ID) {
                    sh """
                        curl -s -X POST "${TEAMS_URL}" \\
                        -H "Content-Type: application/json" \\
                        -d '{
                            "status": "ended",
                            "job": "${env.JOB_SHORT}",
                            "environment": "Development",
                            "branch": "${env.SOURCE_BRANCH}",
                            "committed_by": "${env.COMMITTED_BY}",
                            "commit_message": "${env.COMMIT_MSG}",
                            "pr_url": "${env.PR_URL}",
                            "result": "FAILED"
                        }'
                    """
                }
            }
            echo 'Pipeline failed!'
        }

        always {
            cleanWs()
        }
    }
}
