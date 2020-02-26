stage('Install pip dependencies') {
  steps {
    script {
      if (sh(returnStatus: true, script: 'pip3 install --user -r ./requirements.txt') != 0) {
        echo 'WARNING: Failed to install dependencies from requirements.txt.'
      }
    }
  }
}
stage('Run checks') {
  failFast false
  parallel {
    stage('Invoke Flake8') {
      steps {
        sh 'flake8'
      }
    }
    stage('Invoke Pylint') {
      steps {
        sh 'pylint-3 --reports=n waiverdb'
      }
    }
  }
}
stage('Run unit tests') {
  steps {
    sh 'cp conf/settings.py.example conf/settings.py'
    // wait for the test datebase to come up
    sh 'wait-for-it -s -t 300 127.0.0.1:5432'
    // create a database role
    sh "psql -h 127.0.0.1 -U postgres -q -d waiverdb -c 'CREATE ROLE \"jenkins\" WITH LOGIN SUPERUSER;'"
    // run unit tests
    sh 'py.test-3 -v --junitxml=junit-tests.xml tests'
  }
  post {
    always {
      junit 'junit-tests.xml'
    }
  }
}
stage('Build Artifacts') {
  failFast false
  parallel {
    stage('Branch Docs') {
      stages {
        stage('Build Docs') {
          steps {
            sh 'make -C docs html'
          }
          post {
            always {
              archiveArtifacts artifacts: 'docs/_build/html/**'
            }
          }
        }
        stage('Publish Docs') {
          when {
            expression {
              return "${params.PAGURE_DOC_REPO_NAME}" && (env.GIT_REPO_REF == params.PAGURE_MAIN_BRANCH || env.FORCE_PUBLISH_DOCS == "true")
            }
          }
          steps {
            sshagent (credentials: ["${env.TRIGGER_NAMESPACE}-${params.PAGURE_DOC_SECRET}"]) {
              sh '''
              mkdir -p ~/.ssh/
              touch ~/.ssh/known_hosts
              ssh-keygen -R pagure.io
              echo 'pagure.io ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC198DWs0SQ3DX0ptu+8Wq6wnZMrXUCufN+wdSCtlyhHUeQ3q5B4Hgto1n2FMj752vToCfNTn9mWO7l2rNTrKeBsELpubl2jECHu4LqxkRVihu5UEzejfjiWNDN2jdXbYFY27GW9zymD7Gq3u+T/Mkp4lIcQKRoJaLobBmcVxrLPEEJMKI4AJY31jgxMTnxi7KcR+U5udQrZ3dzCn2BqUdiN5dMgckr4yNPjhl3emJeVJ/uhAJrEsgjzqxAb60smMO5/1By+yF85Wih4TnFtF4LwYYuxgqiNv72Xy4D/MGxCqkO/nH5eRNfcJ+AJFE7727F7Tnbo4xmAjilvRria/+l' >>~/.ssh/known_hosts
              rm -rf docs-on-pagure
              git clone ssh://git@pagure.io/docs/${params.PAGURE_DOC_REPO_NAME}.git docs-on-pagure
              rm -rf docs-on-pagure/*
              cp -r docs/_build/html/* docs-on-pagure/
              cd docs-on-pagure
              git config user.name 'Pipeline Bot'
              git config user.email "pipeline-bot@localhost.localdomain"
              git add -A .
              if [[ "$(git diff --cached --numstat | wc -l)" -eq 0 ]] ; then
                  exit 0 # No changes, nothing to commit
              fi
              git commit -m "Automatic commit of docs built by Jenkins job ${JOB_NAME} #${BUILD_NUMBER}"
              git push origin master
              '''
            }
          }
        }
      }
    }
    stage('Build SRPM') {
      steps {
        sh './rpmbuild.sh -bs'
      }
      post {
        success {
          archiveArtifacts artifacts: 'rpmbuild-output/*.src.rpm'
        }
      }
    }
    stage('Branch RPM') {
      stages {
        stage('Build RPM') {
          steps {
            sh './rpmbuild.sh -bb'
          }
          post {
            success {
              archiveArtifacts artifacts: 'rpmbuild-output/*/*.rpm'
            }
          }
        }
        stage('Invoke Rpmlint') {
          steps {
            sh 'rpmlint -f rpmlint-config.py rpmbuild-output/*/*.rpm'
          }
        }
      }
    }
  }
}
stage('Build container') {
  environment {
    BUILDCONFIG_INSTANCE_ID = "waiverdb-temp-${currentBuild.id}-${UUID.randomUUID().toString().substring(0,7)}"
  }
  steps {
    script {
      // Generate a version-release number for the target Git commit
      def versions = sh(returnStdout: true, script: 'source ./version.sh && echo -en "$WAIVERDB_VERSION\n$WAIVERDB_CONTAINER_VERSION"').split('\n')
      def waiverdb_version = versions[0]
      env.TEMP_TAG = versions[1] + '-jenkins-' + currentBuild.id

      openshift.withCluster() {
        // OpenShift BuildConfig doesn't support specifying a tag name at build time.
        // We have to create a new BuildConfig for each container build.
        // Create a BuildConfig from a seperated Template.
        echo 'Creating a BuildConfig for container build...'
        def template = readYaml file: 'openshift/waiverdb-container-template.yaml'
        def processed = openshift.process(template,
          "-p", "NAME=${env.BUILDCONFIG_INSTANCE_ID}",
          '-p', "WAIVERDB_GIT_REPO=${params.GIT_REPO}",
          // A pull-request branch, like pull/123/head, cannot be built with commit ID
          // because refspec cannot be customized in an OpenShift build .
          '-p', "WAIVERDB_GIT_REF=${env.PR_NO ? env.GIT_REPO_REF : env.GIT_COMMIT}",
          '-p', "WAIVERDB_IMAGE_TAG=${env.TEMP_TAG}",
          '-p', "WAIVERDB_VERSION=${waiverdb_version}",
          '-p', "WAIVERDB_IMAGESTREAM_NAME=${params.IMAGESTREAM_NAME}",
          '-p', "WAIVERDB_IMAGESTREAM_NAMESPACE=${params.IMAGESTREAM_NAMESPACE}",
        )
        def build = c3i.buildAndWait(script: this, objs: processed)
        echo 'Container build succeeds.'
        def ocpBuild = build.object()
        env.RESULTING_IMAGE_REF = ocpBuild.status.outputDockerImageReference
        env.RESULTING_IMAGE_DIGEST = ocpBuild.status.output.to.imageDigest
        def imagestream = openshift.selector('is', ['app': env.BUILDCONFIG_INSTANCE_ID]).object()
        env.RESULTING_IMAGE_REPOS = imagestream.status.dockerImageRepository
        env.RESULTING_TAG = env.TEMP_TAG
      }
    }
  }
  post {
    failure {
      echo "Failed to build container image ${env.TEMP_TAG}."
    }
    cleanup {
      script {
        openshift.withCluster() {
          echo 'Tearing down...'
          openshift.selector('bc', [
            'app': env.BUILDCONFIG_INSTANCE_ID,
            'template': 'waiverdb-container-template',
            ]).delete()
        }
      }
    }
  }
}
stage("Functional tests phase") {
  stages {
    stage('Prepare') {
      steps {
        script {
          env.IMAGE = "${env.RESULTING_IMAGE_REPOS}:${env.RESULTING_TAG}"
        }
      }
    }
    stage('Cleanup') {
      // Cleanup all test environments that were created 1 hour ago in case of failures of previous cleanups.
      steps {
        script {
          openshift.withCluster() {
            openshift.withProject(env.PIPELINE_ID) {
              c3i.cleanup(script: this, age: 60, 'waiverdb')
            }
          }
        }
      }
    }
    stage('Run functional tests') {
      environment {
        // Jenkins BUILD_TAG could be too long (> 63 characters) for OpenShift to consume
        TEST_ID = "${params.TEST_ID ?: 'jenkins-' + currentBuild.id + '-' + UUID.randomUUID().toString().substring(0,7)}"
      }
      steps {
        echo "Container image ${env.IMAGE} will be tested."
        script {
          openshift.withCluster() {
            // Don't set ENVIRONMENT_LABEL in the environment block! Otherwise you will get 2 different UUIDs.
            env.ENVIRONMENT_LABEL = "test-${env.TEST_ID}"
            def template = readYaml file: 'openshift/waiverdb-test-template.yaml'
            def webPodReplicas = 1 // The current quota in UpShift is agressively limited
            echo "Creating testing environment with TEST_ID=${env.TEST_ID}..."
            def models = openshift.process(template,
              '-p', "TEST_ID=${env.TEST_ID}",
              '-p', "WAIVERDB_APP_IMAGE=${env.IMAGE}",
              '-p', "WAIVERDB_REPLICAS=${webPodReplicas}",
            )
            c3i.deployAndWait(script: this, objs: models, timeout: 15)
            def appPod = openshift.selector('pods', ['environment': env.ENVIRONMENT_LABEL, 'service': 'web']).object()
            env.IMAGE_DIGEST = appPod.status.containerStatuses[0].imageID.split('@')[1]
            // Run functional tests
            def route_hostname = openshift.selector('routes', ['environment': env.ENVIRONMENT_LABEL]).object().spec.host
            echo "Running tests against https://${route_hostname}/"
            withEnv(["WAIVERDB_TEST_URL=https://${route_hostname}/"]) {
              sh 'py.test-3 -v --junitxml=junit-functional-tests.xml functional-tests/'
            }
          }
        }
      }
      post {
        always {
          script {
            junit 'junit-functional-tests.xml'
            archiveArtifacts artifacts: 'junit-functional-tests.xml'
            openshift.withCluster() {
              /* Extract logs for debugging purposes */
              openshift.selector('deploy,pods', ['environment': env.ENVIRONMENT_LABEL]).logs()
            }
          }
        }
        cleanup {
          script {
            openshift.withCluster() {
              /* Tear down everything we just created */
              echo "Tearing down test resources..."
              try {
                openshift.selector('dc,deploy,rc,configmap,secret,svc,route',
                      ['environment': env.ENVIRONMENT_LABEL]).delete()
              } catch (e) {
                echo "Failed to tear down test resources: ${e.message}"
              }
            }
          }
        }
      }
    }
  }
  post {
    always {
      script {
        if (!env.IMAGE_DIGEST) {
          // Don't send a message if the job fails before getting the image digest.
          return;
        }
        if (!env.MESSAGING_PROVIDER) {
          // Don't send a message if messaging provider is not configured
          return
        }
        // currentBuild.result == null || currentBuild.result == 'SUCCESS' indicates a successful build,
        // because it's possible that the pipeline engine hasn't set the value nor seen an error when reaching to this line.
        // See example code in https://jenkins.io/doc/book/pipeline/jenkinsfile/#deploy
        def sendResult = sendCIMessage \
          providerName: params.MESSAGING_PROVIDER, \
          overrides: [topic: 'VirtualTopic.eng.ci.container-image.test.complete'], \
          messageType: 'Custom', \
          messageProperties: '', \
          messageContent: """
          {
            "ci": {
              "name": "C3I Jenkins",
              "team": "DevOps",
              "url": "${env.JENKINS_URL}",
              "docs": "https://pagure.io/waiverdb/blob/master/f/openshift",
              "irc": "#pnt-devops-dev",
              "email": "pnt-factory2-devel@redhat.com",
              "environment": "stage"
            },
            "run": {
              "url": "${env.BUILD_URL}",
              "log": "${env.BUILD_URL}/console",
              "debug": "",
              "rebuild": "${env.BUILD_URL}/rebuild/parametrized"
            },
            "artifact": {
              "type": "container-image",
              "repository": "factory2/waiverdb",
              "digest": "${env.IMAGE_DIGEST}",
              "nvr": "${env.IMAGE}",
              "issuer": "c3i-jenkins",
              "scratch": ${params.GIT_REPO_REF != params.PAGURE_MAIN_BRANCH},
              "id": "waiverdb@${env.IMAGE_DIGEST}"
            },
            "system":
               [{
                  "os": "${params.JENKINS_AGENT_IMAGE}",
                  "provider": "openshift",
                  "architecture": "x86_64"
               }],
            "type": "integration",
            "category": "dev",
            "status": "${currentBuild.result == null || currentBuild.result == 'SUCCESS' ? 'passed':'failed'}",
            "xunit": "${env.BUILD_URL}/artifacts/junit-functional-tests.xml",
            "generated_at": "${new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))}",
            "namespace": "c3i",
            "version": "0.1.0"
          }
          """
        if (sendResult.getMessageId()) {
          // echo sent message id and content
          echo 'Successfully sent the test result to ResultsDB.'
          echo "Message ID: ${sendResult.getMessageId()}"
          echo "Message content: ${sendResult.getMessageContent()}"
        } else {
          echo 'Failed to sent the test result to ResultsDB.'
        }
      }
    }
  }
}
