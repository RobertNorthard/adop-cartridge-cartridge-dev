// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Jobs
def createCartridgePackageJob = freeStyleJob(projectFolderName + "/createCartridgePackageJob")
def createValidateCartridgeRepoJob = freeStyleJob(projectFolderName + "/ValidateCartridgeRepo")
def createSonarAnalysisCartridgeJob = freeStyleJob(projectFolderName + "/SonarAnalysisCartridgeRepo")
def createTestCartridgeRepoJob = freeStyleJob(projectFolderName + "/UnitTestCartridgeRepo")

// Views
def pipelineView = buildPipelineView(projectFolderName + "/Cartridge_CI_Pipeline")

pipelineView.with {
    title('Cartirdge CI Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/createCartridgePackageJob")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

// Setup Job
createCartridgePackageJob.with{
   parameters{
      stringParam("CARTRIDGE_REPO","ssh://jenkins@gerrit:29418/${projectFolderName}/my-new-cartridge","Git URL of the cartridge you want to validate.")
   }
   environmentVariables {
       env('WORKSPACE_NAME',workspaceFolderName)
       env('PROJECT_NAME',projectFolderName)
   }
   wrappers {
       preBuildCleanup()
       injectPasswords()
       maskPasswords()
       sshAgent("adop-jenkins-master")
   }
   publishers {
       archiveArtifacts("**/*")
       downstreamParameterized {
           trigger(projectFolderName + "/SonarAnalysisCartridgeRepo") {
               condition("UNSTABLE_OR_BETTER")
               parameters {
                   predefinedProp("BUILD_NUMBER", '${BUILD_NUMBER}')
                   predefinedProp("PARENT_BUILD", '${JOB_NAME}')
               }
           }
       }
   }
}

createSonarAnalysisCartridgeJob.with {
    description("This job runs code quality analysis for cartridge using SonarQube.")
    parameters {
        stringParam("BUILD_NUMBER", '', "Parent build number")
        stringParam("PARENT_BUILD", "", "Parent build to retrieve artefacts from.")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    label("java8")
    steps {
        copyArtifacts('${PARENT_BUILD}') {
            buildSelector {
                buildNumber('${BUILD_NUMBER}')
            }
        }
    }
    configure { myProject ->
        myProject / builders << 'hudson.plugins.sonar.SonarRunnerBuilder'(plugin: "sonar@2.2.1") {
            project('sonar-project.properties')
            properties('''sonar.projectKey=${PROJECT_NAME_KEY}
sonar.projectName=${PROJECT_NAME}
sonar.projectVersion=1.0.${B}
sonar.sources=jenkins/job/dsl
sonar.language=groovy
sonar.sourceEncoding=UTF-8
sonar.scm.enabled=false''')
            javaOpts()
            jdk('(Inherit From Job)')
            task()
        }
    }
    publishers {
        downstreamParameterized {
            trigger(projectFolderName + "/ValidateCartridgeRepo") {
                condition("UNSTABLE_OR_BETTER")
                parameters {
                    predefinedProp("BUILD_NUMBER", '${BUILD_NUMBER}')
                    predefinedProp("PARENT_BUILD", '${JOB_NAME}')
                }
            }
        }
    }
}

// Setup Job
createValidateCartridgeRepoJob.with{
   parameters{
        stringParam("BUILD_NUMBER","","Parent build number.")
        stringParam("PARENT_BUILD","","Parent build to retrieve artifacts from.")
        stringParam("CARTRIDGE_SDK_VERSION","1.0","Cartridge SDK version specification to validate against.")
   }
   environmentVariables {
       env('WORKSPACE_NAME',workspaceFolderName)
       env('PROJECT_NAME',projectFolderName)
   }
   wrappers {
       preBuildCleanup()
       injectPasswords()
       maskPasswords()
       sshAgent("adop-jenkins-master")
   }
   steps {
      copyArtifacts('${PARENT_BUILD}') {
           buildSelector {
               buildNumber('${BUILD_NUMBER}')
           }
       }
       shell('''#!/bin/bash -e

echo
echo

# Checking for SDK version
if [ "$CARTRIDGE_SDK_VERSION" != "1.0" ]; then
 echo Sorry, CARTRIDGE_SDK_VERSION version $CARTRIDGE_SDK_VERSION is not supported by this job
 exit 1
fi

# Checking for existence of files
EXPECTEDFILES="README.md metadata.cartridge src/urls.txt"
for var in ${EXPECTEDFILES}
do

 if [ -f "${var}" ]; then
   echo "Pass: file ${var} exists."
 else
   echo "Fail: file ${var} does not exist."
   exit 1
 fi
done

# Checking for existence of directories
EXPECTEDDIRS="infra jenkins jenkins/jobs jenkins/jobs/dsl jenkins/jobs/xml src .git"
for var in ${EXPECTEDDIRS}
do

 if [ -d "${var}" ]; then
   echo "Pass: directory ${var} exists."
 else
   echo "Fail: directory ${var} does not exist."
   exit 1
 fi
done

# Checking for existence of Jenkins job configs
GCODE=0
cd ${WORKSPACE}/jenkins/jobs/dsl
if ls -la | awk '{ print $9}' | grep .groovy; then
GCODE=1
fi

XCODE=0
cd ${WORKSPACE}/jenkins/jobs/xml
if ls -la | awk '{ print $9}' | grep .xml; then
XCODE=1
fi

if [ $GCODE -eq 1 ]; then
 echo "Pass: Jenkins job (Groovy) config exists."
elif [ $GCODE -eq 0 ] && [ $XCODE -eq 1 ]; then
 echo "Pass: Jenkins job (XML) config exists."
 echo "Note: It is recommended that Groovy is used in favour of XML."
else
 echo "Fail: Jenkins job configs do not exist."
 exit 1
fi

echo
echo PASSED!
echo
      ''')
 }
 publishers {
     downstreamParameterized {
         trigger(projectFolderName + "/UnitTestCartridgeRepo") {
             condition("UNSTABLE_OR_BETTER")
             parameters {
                 predefinedProp("BUILD_NUMBER", '${BUILD_NUMBER}')
                 predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
             }
         }
     }
 }
}

createTestCartridgeRepoJob.with{
    parameters{
        stringParam("BUILD_NUMBER","","Parent build number.")
        stringParam("PARENT_BUILD","","Parent build to retrieve artifacts from.")
    }
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    steps {
        copyArtifacts('${PARENT_BUILD}') {
             buildSelector {
                 buildNumber('${BUILD_NUMBER}')
             }
         }
        shell('''#!/bin/bash -e
./gradlew clean test
       ''')
  }
  publishers{
      publishHtml {
          report('$WORKSPACE') {
              reportName('Cartridge Test Report')
              reportFiles('build/reports/test/index.html')
          }
      }
  }
}
