internal class Service {
	fun teste(){
		val retorno = OtherService().call()

		if(retorno != "OK") {
			throw RuntimeException("Falha executar. Com texto longo aqui que vai indo até chegar numa quebra de página " +
				"porque é muito longo mesmo")
		}

		println("Executado com sucesso. Com texto longo aqui que vai indo até chegar numa quebra de página " +
			"porque é muito longo mesmo")

	}

	fun teste2() {
		OtherService().call()
			.takeIf { retorno -> retorno == "OK" }?.apply {
				println("Executado com sucesso. Com texto longo aqui que vai indo até chegar numa quebra de página " +
					"porque é muito longo mesmo")
			} ?: throw RuntimeException("Falha executar. Com texto longo aqui que vai indo até chegar numa quebra de página " +
			"porque é muito longo mesmo")
	}
}

internal class OtherService {
	fun call(): String {
		return "OK"
	}
}
