#!groovy

gradle = "./gradlew"

buildUrl = "${env.RUN_DISPLAY_URL}".replaceAll('http', 'https').replace('httpss', 'https')
jobUserName = ""

githubProjectPath = "github.com/fhgomes.git"
lastCommiterName = ""
lastCommiterMsg = ""
lastCommitShortHash = ""
toBranch = ""
version = ""

node("nodejs10") {

	def nodeHome = tool 'nodejs10'
	env.PATH = "${nodeHome}/bin:${env.PATH}"

	properties([
			parameters([
					string(
							defaultValue: '',
							description: 'Insira o nome da tag/branch do git',
							name: 'BRANCH'
					),
					booleanParam(
							defaultValue: false,
							description: 'Forçar o build+bump da versão?',
							name: 'FORCE_BUILD_BUMP'
					),
					booleanParam(
							defaultValue: false,
							description: 'Ignorar a verificação de lint?',
							name: 'IGNORE_LINT'
					),
					choice(
							choices: ['none', 'patch', 'minor', 'major'],
							description: 'Qual incremento da versão ?. Default is none.\n' +
									'E.g.: major.minor.patch',
							name: 'BUMP_CHOICE'
					),
					booleanParam(
							defaultValue: true,
							description: 'Compilar com teste unitario?',
							name: 'RUN_TESTS_SIMPLE'
					),
					booleanParam(
							defaultValue: false,
							description: 'Compilar com testes UI?',
							name: 'RUN_TESTS_UI'
					)
			])
	])

	try {

		retrieveJobUserName()
		sendMsg(":point_right: ${jobUserName} iniciou este job [${params.BRANCH}] - QA")
		stage("Clean Workspace") {
			deleteDir()
		}

		checkout()
		npmInstall()
		test()
		validateLint()

		bumpVersion()
		buildPackage()
		processDelivery()

		sendMsg("Finalizado! :dimitri: :gangnam: :yeah:")
	} catch (Exception e) {
		sendMsg("Erro ao executar.:sadparrot: :eyewitch: Causa: "+e.getMessage())
		e.printStackTrace()
		error
	}
}

def retrieveJobUserName() {
	wrap([$class: 'BuildUser']) {
		jobUserName = "${BUILD_USER}"
	}
}

def processDelivery() {
	buildDocker()
	deployQA()
}

def deployQA() {
	stage(name: "Deploy QA") {
		milestone()
		sh "PROFILE=qa npm run deploy-qa"
	}
}

def checkout() {
	disableConcurrentBuilds()
	stage("Checkout") {

		if (params.BRANCH == null || params.BRANCH == '') {
			error("Parametro BRANCH obrigatório")
		}

		sendMsg("Checkout Iniciado [${params.BRANCH}] :ccassol:")

		try {
			checkout changelog: true, poll: true, scm: [
					$class                           : 'GitSCM',
					branches                         : [[name: params.BRANCH]],
					doGenerateSubmoduleConfigurations: false,
					extensions                       : [],
					submoduleCfg                     : [],
					userRemoteConfigs                : [[credentialsId: 'acc_git', url: 'https://'+githubProjectPath]]
			]

			println "get last commiter https://git-scm.com/docs/pretty-formats"
			lastCommiterName = sh(script: "git log -1 --pretty=format:'%an'", returnStdout: true).trim()
			lastCommiterMsg = sh(script: "git log -1 --pretty=format:'%s'", returnStdout: true).trim()
			lastCommitShortHash = sh(script: "git log -1 --pretty=format:'%h'", returnStdout: true).trim()
			echo "My Commiter is: ${lastCommiterName}"
			echo "My commitHash is: ${lastCommitShortHash}"

			toBranch = "${params.BRANCH}".replace("origin/", "")

		} catch (Exception e) {
			sendMsg("<!here>: O Job falhou ao fazer checkout. Detalhes:" + e.getMessage())
			e.printStackTrace()
			throw e
		}
	}
}

private Object bumpVersion() {
	stage(name: "Version") {
		milestone()

		if (params.BUMP_CHOICE != "none") {
			def oldVersion = getVersion()
			sh "npm version ${params.BUMP_CHOICE}"
			commitChanges("Version", true)
			sendMsg("Bump version [${params.BUMP_CHOICE}]: ${oldVersion} -> ${getVersion()}")
		}
		def newHashVersion = getVersion() + "-" + lastCommitShortHash + ".0"
		sh "npm version ${newHashVersion} -m 'version: hash ${newHashVersion}'"

		sendMsg("Versão para fazer deploy: ${getVersion()}")
	}
}

private void buildPackage() {
	stage(name: "Build Client") {
		if (!params.FORCE_BUILD_BUMP) {
			return
		}
		sh "npm run build:client:qa"
	}

	stage(name: "Build Server") {
		if (!params.FORCE_BUILD_BUMP) {
			return
		}
		sh "npm run build:server:qa"
	}
}

private void validateLint() {
	stage("Validate Lint") {
		if (params.IGNORE_LINT) {
			return
		}
		sh "npm run lint"
	}
}

private void test() {
	stage("Test unit") {
		if (params.RUN_TESTS_SIMPLE) {
			sh "npm run test"
		}
	}
	stage("Test UI") {
		if (!params.FORCE_BUILD_BUMP) {
			return
		}

		if (params.RUN_TESTS_UI) {
			sh "${gradle} seleniumTest"
		}
	}
}

private void npmInstall() {
	stage(name: "NPM Install") {
		milestone()
		sh """
			export HTTP_PROXY=http://fhgomes.proxy.srv.intranet:80
			export NO_PROXY="nexus.fhgomes.intranet"
		"""
		sh "NODE_ENV=jenkins npm ci --registry ${NPM_INSTALL_REG}"
	}
}

void buildDocker() {
	stage(name: "Build Docker") {
		if (!params.FORCE_BUILD_BUMP) {
			return
		}
		sh "PROFILE=qa npm run docker-build"
	}

	stage(name: "Upload Nexus") {
		if (!params.FORCE_BUILD_BUMP) {
			return
		}
		sh "PROFILE=qa npm run docker-publish"
		sendMsg("Gerada imagem docker do app [${version}] :docker:")

	}
}

def sendMsg(String message) {
	slack.sendMessage(slackChannel, "[<${buildUrl}|${env.JOB_NAME}>] " + message)
}

def commitChanges(commitMsg, doPush) {
	withCredentials([usernameColonPassword(credentialsId: "acc_git", variable: "token")]) {
		sh "git config remote.origin.url https://${token}@${githubProjectPath}"
		sh "git add ."
		sh "git commit -m '${commitMsg}'"
		if(doPush) {
			sh "git push -u origin ${toBranch}"
		}
	}
}

def getVersion() {
	version = sh(script: "node -e \"console.log(require('./package.json').version);\"", returnStdout: true).trim()
	return version
}
