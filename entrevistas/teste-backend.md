Boa tarde <CANDIDATO/A>, conforme falamos hoje, segue o descritivo para criação do projeto simplificado de teste;
Com base nele faremos nossa entrevista técnica com live-coding dia DATA+HORA
Pode avisar o término por e-mail ou Whats, e subir no GitHub em um repositório public. Prazo até nossa call 

Lembrando que na call faremos um live-coding rápido com objetivo de ver seus conhecimentos e modo de trabalhar, e isso é mais importante do que ter tudo implementando e funcionando 100%.


Vamos desenvolver uma plataforma nova, iremos criar o BCB – Big Chat Brasil, o mais novo e interessante enviador de SMS e outras mensagens brasileiro. 
Precisamos que seja possível via web ou mobile que os clientes enviem mensagens para seus usuários finais.
Nesse sistema cada cliente precisa ser previamente cadastrado. Ao receber a solicitação de envio de mensagem deve ser verificado o tipo de plano de pagamento desse cliente.
Caso for pre-pago, esse cliente deve possuir creditos para envio de SMS que custam R$0,25 cada. Caso o cliente seja pós pago, deve registrar o consumo na conta até o limite máximo autorizado.

Dados para cadastro do cliente:
* Nome, e-mail, Telefone, CPF responsável, CNPJ, Nome empresa.

Para envio de SMS deve conter:
* Número telefone, flag se é WhatsApp, texto

No Backoffice, nossos financeiro deve poder fazer operações e disponibilizar dados como:
* Incluir creditos para um cliente
* Consultar saldo de um cliente
* Alterar limite de um cliente
* Alterar plano de um cliente
* Ver dados de um cliente


Instruções gerais:

* escrever as premissas que foram assumidas no desenvolvimento
* deve conter um passo a passo para execução do sistema no readme.md
* entregar no Github

Caso seu teste se aplique mais para a vaga backend:

* backend em Java (spring boot + spring jpa + spring cloud) ou Node + NestJs
* banco de dados de Postgre (pode ser rodando em docker)

Caso seu teste se aplique mais para a vaga frontend:

* na parte web/mobile deve ser possível fazer login simples (não necessita cadastro prévio)
* front com react ou angular
* deve ser responsivo para web e mobile
* backend pode ser real ou simulado/mockado

Caso seja fullstack, e tiver disponibilidade pode fazer ambos os sides.

Pode ser um plus:

* rodar back + front em container
* rodar em kubernetes
* envio de notificações (mesmo que seja via mock)
* uso de processos assíncronos
* outras melhorias de performance que possa ser feita baseada em suas premissas
* uso de testes unitários no back ou no front

Observações: 

* esse teste atende de JR a Especialistas, faça o seu melhor
* se ficar muito apertado, entregue com escopo reduzido
* se não tiver conhecimento no front, entregue apenas o backend para atender 
* caso não tiver conhecimento do backend, utilize mocks e entregue apenas o front
+ collection postman(ou outra ferramenta para testar manualmente)

Pode avisar o término por e-mail. Prazo até nossa call DIA+HORA (a definir de acordo com a disponibilidade do candidato)

Qualquer dúvida estou a disposição,

Boa sorte

Atenciosamente,
