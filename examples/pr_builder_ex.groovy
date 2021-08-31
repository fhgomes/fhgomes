
//BASE_PATH - $.pull_request.head.repo.full_name
//BRANCH - $.pull_request.head.ref
//BRANCH_TARGET - $.pull_request.base.ref
//GIT_COMMIT - $.pull_request.head.sha
//PR_ACTION - $.action
//PR_NAME - $.pull_request.title
//PR_LINK - $.pull_request.html_url

gitAPI = new GithubAPI(this)

gradle = "./gradlew"
slackChannel = "#fernando_ci"
team = "FernandoTeam"

lastCommiterName = ""
lastCommiterMsg = ""
lastCommitShortHash = ""
targetBranch = ""
sourceBranch = ""
prName = ""
prLink = ""

buildUrl = "${env.RUN_DISPLAY_URL}".replaceAll('http', 'https').replace('httpss', 'https')

node("java11") {

	withEnv(["JAVA_HOME=${tool 'java11'}", "JAVA_OPTS=-Xmx1024M -Dfile.encoding=UTF-8 -Duser.timezone=America/Sao_Paulo", "TZ=America/Sao_Paulo"]) {
		try {
			stage("Clean Workspace") {
            	deleteDir()
			}

			println "Branch selecionada: ${env.BRANCH}"

			gitAPI.changeStatus("pending", "pr_builder")
			checkout()

			build()

			test()

			//fortify()

			sonar()
			sendMsg(":clap::champagne: Seu PR: <${prLink}|${prName}> [${sourceBranch}]->[${targetBranch}] está integro")

			gitAPI.changeStatus("success", "pr_builder")
		} catch (Exception e) {
			deleteDir()
			sendMsg(":failed: PR: <${prLink}|${prName}> [${sourceBranch}]->[${targetBranch}] falhou!. Detalhes: " + e.getMessage())
			e.printStackTrace()
			gitAPI.changeStatus("failure", "pr_builder")
		}
	}
}

def checkout() {
	stage("Checkout") {

		currentBuild.displayName = "#${env.BUILD_NUMBER} - ${env.BRANCH}"
		echo "Branch is: ${env.BRANCH}"
		checkout scm

		prName = env.PR_NAME
		prLink = env.PR_LINK
		targetBranch = env.BRANCH_TARGET
		sourceBranch = env.BRANCH

		println "get last commiter https://git-scm.com/docs/pretty-formats"
		lastCommiterName = sh(script: 'git log -1 --pretty=format:\'%an\'', returnStdout: true).trim()
		lastCommiterMsg = sh(script: 'git log -1 --pretty=format:\'%s\'', returnStdout: true).trim()
		lastCommitShortHash = sh(script: 'git log -1 --pretty=format:\'%h\'', returnStdout: true).trim()
		sendMsg("Validando a PR: <${prLink}|${prName}> [${sourceBranch}]->[${targetBranch}] com ultimo commit de ${lastCommiterName}")
	}
}

def build() {
	stage("Build") {
		sh "${gradle} clean build -x test --stacktrace"
	}
}

def test() {
	stage("Test unit") {
		sh "${gradle} test -i"

	}
	stage("Test componentes") {
//		sh "${gradle} test -i"
	}
}

def fortify() {
	stage(name: "Fortify") {
		def ROOT_DIR = ""
		def PROJECT = "PJ"
		def PROJECT_NAME_FORTIFY = "PROJECT_NAME_FORTIFY"

		try {
			build job: 'COMMONS/fortify-generic',
				parameters: [
					string(name: 'PROJECT_NAME_FORTIFY', value: PROJECT_NAME_FORTIFY),
					string(name: 'BRANCH', value: $ { params.BRANCH }),
					string(name: 'ROOT_DIR', value: ROOT_DIR),
					string(name: 'CANAL_TIME', value: slackChannel),
					string(name: 'STASH_PROJECT', value: PROJECT),
					string(name: 'SQUAD_DEV', value: team)
				],
				wait: false
		} catch (Exception e) {
			sendMsg("<!here>: Fortify (ERRO) -  <${buildUrl}job/fortify-generic|fortify-generic>")
			e.printStackTrace()
			throw e
		}
	}
}

def sonar() {
	stage("Sonar") {
		withSonarQubeEnv("sonarServer1") {
			try {
				sh "${gradle} jacocoTestReport sonar -Dsonar.branch.name=${targetBranch}_${sourceBranch} -x test --info --stacktrace"
			} catch (Exception e) {
				sendMsg("<!here>: Erro ao rodar analise do sonar")
				e.printStackTrace()
				throw e
			}
		}
	}

	stage("Quality result?") {
		if(false){
			timeout(time: 5, unit: 'MINUTES') {
				def qg = waitForQualityGate()
				print 'Resultado do Sonar: ' + qg
				print 'Resultado do Sonar status: ' + qg.status
				if (qg.status != 'OK') {
					error "Pipeline aborted due to quality gate failure: ${qg.status}"
					sendMsg("<!here>: Moises não passou no QG do sonar: "+ ${qg.status})
				}
			}
		}
	}
}

def sendMsg(String message) {
	slack.sendMessage(slackChannel, "[<${buildUrl}|${env.JOB_NAME}>] " + message)
}
