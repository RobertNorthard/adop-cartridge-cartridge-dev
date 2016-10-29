
// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// jobs
def createNewGenerateCartridgeCIPipelineJob = freeStyleJob(projectFolderName + "/GenerateCartridgeCIPipeline")

def logRotatorBuildsToKeep = 5
def logRotatorNumToKeep = 7
def logRotatorArtifactsToKeep = 7
def logRotatorArtifactsNumToKeep = 7

def gerritGitRepoAccessCredentialsKeyName = "adop-jenkins-master"

createNewGenerateCartridgeCIPipelineJob.with{
   description("This 'Seed Job' generates a CI pipeline for the specified cartridge.")
   logRotator {
      daysToKeep(logRotatorBuildsToKeep)
      numToKeep(logRotatorNumToKeep)
      artifactDaysToKeep(logRotatorArtifactsToKeep)
      artifactNumToKeep(logRotatorArtifactsNumToKeep)
   }
   parameters{
      stringParam("CARTRIDGE_NAME", "", "Cartridge name. Note: This adds it to the Sonar projec e.g. adop-cartridge-my-new-cartridge.")
      stringParam("CARTRIDGE_GIT_URL", "ssh://jenkins@gerrit:29418/${projectFolderName}/my-new-cartridge", "Cartridge URL to load. Note: The adop-jenkins-master SSH key must have permission to clone the repository.")
      stringParam("CARTRIDGE_GIT_BRANCH", "*/master", "Cartridge Git branch.")
   }
   environmentVariables {
      env('WORKSPACE_NAME', workspaceFolderName)
      env('PROJECT_NAME', projectFolderName)
  }
  wrappers {
      preBuildCleanup()
      injectPasswords()
      maskPasswords()
      sshAgent(gerritGitRepoAccessCredentialsKeyName)
   }
  steps{
       dsl {
          text(readFileFromWorkspace('cartridge/jenkins/jobs/dsl/TestCartridgePipeline.template'))
       }
  }
}
