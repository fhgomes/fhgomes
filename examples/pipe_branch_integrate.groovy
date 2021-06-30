#!groovy

slack = new SlackAPI(this)

gradle = "./gradlew"
slackChannel = "#slack"
team = "Fer"

lastCommiterName = ""
lastCommitShortHash = ""
fromBranch = ""
targetBranch = "master"

buildUrl = "${env.RUN_DISPLAY_URL}".replaceAll('http', 'https').replace('httpss', 'https')
jobUserName = ""

approvalTime = 6
approvalUnit = "HOURS"


node("java11") {

	properties([
		parameters([
			string(
				defaultValue: '',
				description: 'Insira o nome da tag/branch do git',
				name: 'BRANCH'
			),
			booleanParam(
				defaultValue: true,
				description: 'Integrar com develop ?',
				name: 'MERGE_DEVELOP'
			),
			choice(
				choices: ['none', 'patch', 'minor', 'major'],
				description: 'Qual incremento da versão ?. Default is none.\n' +
					'E.g.: major.minor.patch',
				name: 'BUMP_CHOICE'
			),
			booleanParam(
				defaultValue: true,
				description: 'Compilar com testes completo (integração)?',
				name: 'RUN_TESTS_COMPLETE'
			),
			booleanParam(
				defaultValue: true,
				description: 'Compilar com testes UI/Integration?',
				name: 'RUN_TESTS_UI'
			),
			booleanParam(
				defaultValue: true,
				description: 'Analise de Qualidade (SONAR)?',
				name: 'RUN_SONAR'
			),
			booleanParam(
				defaultValue: true,
				description: 'Parar se o sonar falhar ?',
				name: 'STOP_ON_SONAR_ERROR'
			)
		])
	])

	withEnv(["JAVA_HOME=${tool 'java11'}", "JAVA_OPTS=-Xmx1024M -Dfile.encoding=UTF-8 -Duser.timezone=America/Sao_Paulo", "TZ=America/Sao_Paulo"]) {
		try {
			println "Branch selecionada: ${params.BRANCH}"
			retrieveJobUserName()
			sendMsg(":point_right: ${jobUserName} iniciou esse job [${params.BRANCH}] - Integração Master")

			requireApprove()

			stage("Clean Workspace") {
				deleteDir()
			}
			checkout()
			processMergeMaster()
			build()
			test()
			sonar()

			processPushMaster()
			processDevelopment()

			executeGitCmd("push origin --delete ${fromBranch}")

			sendMsg("Integração[${fromBranch}] realizada com sucesso na branch: ${targetBranch}")

		} catch (Exception e) {
			sendMsg("<!here>: O Job de integração falhou: " + e.getMessage())
			e.printStackTrace()
			error
		}
	}
}

def requireApprove() {
	stage(name: "Approval", concurrency: 1)
	timeout(time: approvalTime, unit: approvalUnit) {
		sendMsg("<!here>: Aguardando aprovação para integrar a branch na ${targetBranch}!.")
		input(message: "Approve Integration?")
	}
}

def retrieveJobUserName() {
	wrap([$class: 'BuildUser']) {
		jobUserName = "${BUILD_USER}"
	}
}

def processMergeMaster() {
	stage("Merge Target") {
		executeGitCmd("fetch --all")
		executeGitCmd("checkout ${targetBranch}")
		executeGitCmd("merge origin/${fromBranch} -m 'Realizando integracao de versao' --no-ff")
	}
}

def processPushMaster() {
	stage("Push") {
		executeGitCmd("push -u origin ${targetBranch}")
	}
}

def processDevelopment() {
	stage("Merge Develop?") {
		try {
			if (params.MERGE_DEVELOP) {
				processMergeDevelopment()
				build()
				test()
				processPushDevelopment()
			}
		} catch (Exception e) {
			sendMsg("<!here>: Falha ao integrar[${fromBranch}] com a Development. " + e.getMessage())
			currentBuild.result = 'UNSTABLE'
		}
	}
}

def processMergeDevelopment() {
	executeGitCmd("fetch --all")
	executeGitCmd("checkout development")
	executeGitCmd("merge origin/${fromBranch} -m 'Realizando integracao de versao' --no-ff")
}

def processPushDevelopment() {
	executeGitCmd("push -u origin development")
}

def checkout() {
	stage("Checkout") {

		if (params.BRANCH == null || params.BRANCH == '') {
			error("Parametro branch obrigatório")
		}

		sendMsg("Checkout Iniciado [${params.BRANCH}] ")

		try {
			checkout changelog: true, poll: true, scm: [
				$class                           : 'GitSCM',
				branches                         : [[name: params.BRANCH]],
				doGenerateSubmoduleConfigurations: false,
				extensions                       : [],
				submoduleCfg                     : [],
				userRemoteConfigs                : [[credentialsId: 'acc_git', url: 'https://github.com/fhgomes.git']]
			]

			println "get last commiter https://git-scm.com/docs/pretty-formats"
			lastCommiterName = sh(script: 'git log -1 --pretty=format:\'%an\'', returnStdout: true).trim()
			lastCommitShortHash = sh(script: 'git log -1 --pretty=format:\'%h\'', returnStdout: true).trim()
			echo "My Commiter is: ${lastCommiterName}"
			echo "My commitHash is: ${lastCommitShortHash}"

			fromBranch = "${params.BRANCH}".replace("origin/", "")
			projectRelease = new ProjectRelease(this, "${fromBranch}", "${params.BUMP_CHOICE}")
		} catch (Exception e) {
			sendMsg("<!here>: O Job falhou ao fazer checkout. Detalhes:" + e.getMessage())
			e.printStackTrace()
			throw e
		}
	}
}



def build() {
	stage("Build") {
		sh "${gradle} clean build -x test --stacktrace"
	}
}

def test() {
	stage("Test unit") {
		try {
			sh "${gradle} test -i"
		} catch (Exception e) {
			sendMsg("<!here>: Test unit falhou. Detalhes: " + e.getMessage())
			e.printStackTrace()
			throw e
		}
	}

	stage("Test components") {
		try {
			if (params.RUN_TESTS_COMPLETE) {
				sh "${gradle} integrationTest -i"
			}
		} catch (Exception e) {
			sendMsg("<!here>: Test components falhou. Detalhes: " + e.getMessage())
			e.printStackTrace()
			throw e
		}
	}

	stage("Test UI") {
		try {
			if (params.RUN_TESTS_UI) {
				sh "${gradle} seleniumTest"
			}
		} catch (Exception e) {
			sendMsg("<!here>: Test UI falhou. Detalhes: " + e.getMessage())
			e.printStackTrace()
			throw e
		}
	}

	always {
		junit allowEmptyResults: true, keepLongStdio: true, testResults: '**/build/test-results/test/TEST-*.xml'
	}
}

def sonar() {
	stage("Sonar") {
		if(params.FORCE_BUILD && params.RUN_SONAR) {
			withSonarQubeEnv("sonarServerMesos") {
				try {
					sh "${gradle} jacocoTestReport sonar -Dsonar.branch.name=${params.BRANCH} -x test --info --stacktrace"
				} catch (Exception e) {
					sendMsg("<!here>: Erro ao rodar analise do sonar. Detalhes: " + e.getMessage())
					e.printStackTrace()
					throw e
				}
			}
		}
	}

	stage("Quality result?") {
		if (params.FORCE_BUILD && params.RUN_SONAR && params.STOP_ON_SONAR_ERROR) {
			timeout(time: 5, unit: 'MINUTES') {
				def qg = waitForQualityGate()
				print 'Resultado do Sonar: ' + qg
				print 'Resultado do Sonar status: ' + qg.status
				if (qg.status != 'OK') {
					error "Pipeline aborted due to quality gate failure: ${qg.status}"
					sendMsg("<!here>: Moises não passou no QG do sonar: " + $ { qg.status })
				}
			}
		}
	}
}

String getVersion() {
	def content = readFile(pwd() + "/gradle.properties")

	for (def line in content.split('\n')) {
		if (line =~ /version/) {
			return line.split('=')[1]
		}
	}

	return ''
}

def sendMsg(String message) {
	slack.sendMessage(slackChannel, "[<${buildUrl}|${env.JOB_NAME}>] "+ message)
}

def executeGitCmd(command) {
	withCredentials([usernameColonPassword(credentialsId: 'acc_git', variable: 'token')]) {
		sh "git config remote.origin.url https://${token}@github.com/fhgomes.git"
		sh "git ${command}"
	}
}

def commitChanges(commitMsg, doPush) {
	withCredentials([usernameColonPassword(credentialsId: 'acc_git', variable: 'token')]) {
		sh "git config remote.origin.url https://${token}@github.com/fhgomes.git"
		sh 'git add .'
		sh "git commit -m \"${commitMsg}\""
		if(doPush) {
			sh "git push -u origin ${targetBranch}"
		}
	}
}
