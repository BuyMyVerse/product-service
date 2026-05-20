pipeline {
    agent any

    stages {
        stage('Where am I?') {
            steps {
                sh '''
                    echo "Hostname: $(hostname)"
                    echo "IP Address: $(hostname -I)"
                    echo "Current Directory: $(pwd)"
                    echo "User: $(whoami)"
                '''
            }
        }
    }
}
