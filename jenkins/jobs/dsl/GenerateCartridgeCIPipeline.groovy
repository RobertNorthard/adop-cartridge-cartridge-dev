
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
      stringParam("CARTRIDGE_CLONE_URL", "ssh://jenkins@gerrit:29418/${projectFolderName}/my-new-cartridge", "Cartridge URL to load.")
      stringParam("CARTRIDGE_NAME", "", "Cartridge name.")
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
   dsl {
      text(readFileFromWorkspace('cartridge/jenkins/jobs/dsl/TestCartridgePipeline.template'))
   }
}
