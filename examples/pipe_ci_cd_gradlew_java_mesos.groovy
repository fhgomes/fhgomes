#!groovy
slack = new SlackAPI(this)

gradle = "./gradlew"
slackChannel = "#channel"

lastCommiterName = ""
lastCommitShortHash = ""
toBranch = ""

buildUrl = "${env.RUN_DISPLAY_URL}".replaceAll('http', 'https').replace('httpss', 'https')
jobUserName = ""

node("java11") {
	properties([
		parameters([
			string(
				defaultValue: '',
				description: 'Insira o nome da tag/branch do git',
				name: 'BRANCH'
			),
			booleanParam(
				defaultValue: false,
				description: 'Realizar o build e gerar nova imagem?',
				name: 'FORCE_BUILD'
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
				description: 'Compilar com testes completo (integração)?',
				name: 'RUN_TESTS_COMPLETE'
			),
			booleanParam(
				defaultValue: false,
				description: 'Compilar com testes UI/Integration?',
				name: 'RUN_TESTS_UI'
			),
			booleanParam(
				defaultValue: true,
				description: 'Analise de Qualidade (SONAR)?',
				name: 'RUN_SONAR'
			),
			booleanParam(
				defaultValue: false,
				description: 'Parar se o sonar falhar ?',
				name: 'STOP_ON_SONAR_ERROR'
			)
		])
	])

	withEnv(["JAVA_HOME=${tool 'java11'}", "JAVA_OPTS=-Xmx1024M -Dfile.encoding=UTF-8 -Duser.timezone=America/Sao_Paulo", "TZ=America/Sao_Paulo"]) {
		try {
			configProxy()
			retrieveJobUserName()
			sendMsg(":point_right: ${jobUserName} iniciou esse job [${params.BRANCH}] - QA")

			stage("Clean Workspace") {
				deleteDir()
			}

			checkout()

			build()

			test()

			sonar()

			processDelivery()

		} catch (Exception e) {
			sendMsg("<!here>: O Job de CI falhou. Detalhes: " + e.getMessage())
			e.printStackTrace()
			error
		}
	}
}

def retrieveJobUserName() {
	wrap([$class: 'BuildUser']) {
		jobUserName = "${BUILD_USER}"
	}
}

def checkout() {
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
				userRemoteConfigs                : [[credentialsId: 'svcaccgithub', url: 'https://github.com/fhgomes.git']]
			]

			println "get last commiter https://git-scm.com/docs/pretty-formats"
			lastCommiterName = sh(script: 'git log -1 --pretty=format:\'%an\'', returnStdout: true).trim()
			lastCommitShortHash = sh(script: 'git log -1 --pretty=format:\'%h\'', returnStdout: true).trim()
			echo "My Commiter is: ${lastCommiterName}"
			echo "My commitHash is: ${lastCommitShortHash}"

			toBranch = "${params.BRANCH}".replace("origin/", "")
			projectRelease = new ProjectRelease(this, "${toBranch}", "${params.BUMP_CHOICE}")
		} catch (Exception e) {
			sendMsg("<!here>: O Job falhou ao fazer checkout. Detalhes:" + e.getMessage())
			e.printStackTrace()
			throw e
		}
	}
}

def build() {
	stage("Build") {
		if (!params.FORCE_BUILD) {
			sendMsg("Skipping build steps.")
			return
		}
		sh "${gradle} clean build -x test --stacktrace"
	}
}

def test() {
	stage("Test unit") {
		try {
			if (!params.FORCE_BUILD) {
				return
			}

			if (params.RUN_TESTS_SIMPLE) {
				sh "${gradle} test -i"
			}
		} catch (Exception e) {
			sendMsg("<!here>: Test unit falhou. Detalhes: " + e.getMessage())
			e.printStackTrace()
			throw e
		}
	}

	stage("Test components") {
		if (!params.FORCE_BUILD) {
			return
		}

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
		if (!params.FORCE_BUILD) {
			return
		}

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

def processDelivery() {
	release()
	deliveryQA()
}

def release() {
	bumpVersion()

	releaseDockerImage()
}

def bumpVersion() {
	stage(name: "Bump Version") {
		if (!params.FORCE_BUILD) {
			return
		}

		if (params.BUMP_CHOICE == 'none') {
			sendMsg("SKIP Bump version [${params.BUMP_CHOICE}]")
			return
		}

		try {
			milestone()
			def lastVersion = getVersion()
			def newVersion = release.incrementVersion(lastVersion)

			withCredentials([usernameColonPassword(credentialsId: 'acc_github', variable: 'token')]) {
				release.updateGradleVersion(newVersion)
				release.commitVersion(newVersion)
			}

			sendMsg("Bump version [${params.BUMP_CHOICE}]: ${lastVersion} -> ${newVersion}")
		} catch (Exception e) {
			sendMsg("<!here>: Bump version falhou. Detalhes: " + e.getMessage())
			e.printStackTrace()
			throw e
		}
	}
}

def releaseDockerImage() {
	stage(name: "Release QA App Docker") {
		if (!params.FORCE_BUILD) {
			return
		}

		try {
			def imageVersion = "${getVersion()}_${lastCommitShortHash}"

			docker.withRegistry('https://registry.intranet.fhgomes', 'acc_jenkins') {
				def dockerImage = docker.build("fhgomes-api:${imageVersion}", "./")
				dockerImage.push()
			}
			commitChanges("Update docker app version ${imageVersion}", true)

			sendMsg("Gerada imagem docker do app [${imageVersion}] :docker:")
		} catch (Exception e) {
			sendMsg("<!here>: A geração da imagem do app falhou. :docker: Detalhes: " + e.getMessage())
			e.printStackTrace()
			throw e
		}
	}
}

def deliveryQA() {
	stage(name: "Deploy QA") {
		milestone()
		try {
			sendMsg("Delivery QA Iniciado [${params.BRANCH}]")

			mesos.deployTo("qa-aws")

			sendMsg("Delivery QA Finalizado com Sucesso")
		} catch (Exception e) {
			sendMsg("<!here>: Delivery QA falhou. Tente com FORCE_BUMP. Detalhes: " + e
				.getMessage() + " :dorime:")
			e.printStackTrace()
			throw e
		}
	}
}

def fortify() {
	stage(name: "Fortify") {
		if (!params.FORCE_BUILD) {
			return
		}

		def ROOT_DIR = ""
		def PROJECT_NAME_FORTIFY = "projeto"

		try {
			build job: 'COMMONS/fortify-generic',
				parameters: [
					string(name: 'PROJECT_NAME_FORTIFY', value: PROJECT_NAME_FORTIFY),
					string(name: 'BRANCH', value: $ { params.BRANCH }),
					string(name: 'ROOT_DIR', value: ROOT_DIR),
					string(name: 'CANAL_TIME', value: slackChannel),
					string(name: 'PROJECT', value: PROJECT),
					string(name: 'SQUAD_DEV', value: team)
				],
				wait: false
		} catch (Exception e) {
			sendMsg("<!here>: Fortify (ERRO) -  <${env.JENKINS_URL}job/fortify-generic|fortify-generic>")
			e.printStackTrace()
			throw e
		}
	}
}

def sonar() {
	stage("Sonar") {
		if(params.FORCE_BUILD && params.RUN_SONAR) {
			withSonarQubeEnv("sonarServer1") {
				try {
					sh "${gradle} jacocoTestReport sonar -Dsonar.branch.name=${params.BRANCH} -x test --info --stacktrace"
				} catch (Exception e) {
					sendMsg("<!here>: Erro ao rodar analise do sonar. Tem um espaço antes/depois da branch? Detalhes: " + e.getMessage())
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

def configProxy() {
	sh "export http_proxy=http://proxy.aws.intranet:80"
	sh "export https_proxy=http://proxy.aws.intranet:80"
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
	slack.sendMessage(slackChannel, "[<${buildUrl}|${env.JOB_NAME}>] " + message)
}

def commitChanges(commitMsg, doPush) {
	withCredentials([usernameColonPassword(credentialsId: 'svcaccgithub', variable: 'token')]) {
		sh "git config remote.origin.url https://${token}@github.com/fhgomes.git"
		sh 'git add .'
		sh "git commit -m \"${commitMsg}\""
		if(doPush) {
			sh "git push -u origin ${toBranch}"
		}
	}
}
