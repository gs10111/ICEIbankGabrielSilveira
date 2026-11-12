import 'dinheiro.dart';

enum EstadoPendente {
  /// Na fila, esperando conexao. Entra no saldo provisorio.
  pendente,

  /// A agencia recusou com 409: a conta mudou enquanto o app estava offline.
  /// NAO entra no saldo provisorio; espera a pessoa decidir (reenviar ou descartar).
  conflito,

  /// A agencia recusou por regra de negocio (saldo insuficiente, por exemplo).
  recusada,
}

/// Uma operacao feita sem conexao, guardada no aparelho ate a agencia aceitar.
///
/// `vetorConhecido` e o vetor que a agencia tinha na ultima leitura do app. E ele que
/// a agencia compara em POST /contas/{id}/sincronizar para saber se ainda e seguro.
class OperacaoPendente {
  final int chave;
  final int idConta;
  final String tipo;                 // DEPOSITO | SAQUE — os que o backend sincroniza
  final int centavos;
  final List<int> vetorConhecido;
  final DateTime criadaEm;
  final EstadoPendente estado;
  final String? mensagem;
  final int? saldoRealCentavos;

  const OperacaoPendente({
    required this.chave,
    required this.idConta,
    required this.tipo,
    required this.centavos,
    required this.vetorConhecido,
    required this.criadaEm,
    this.estado = EstadoPendente.pendente,
    this.mensagem,
    this.saldoRealCentavos,
  });

  bool get ehDeposito => tipo == 'DEPOSITO';

  /// Efeito no saldo provisorio: + deposito, - saque.
  int get efeitoCentavos => ehDeposito ? centavos : -centavos;

  String get descricao => '${ehDeposito ? 'Depósito' : 'Saque'} de ${formatarReais(centavos)}';

  OperacaoPendente com({
    List<int>? vetorConhecido,
    EstadoPendente? estado,
    String? mensagem,
    int? saldoRealCentavos,
  }) =>
      OperacaoPendente(
        chave: chave,
        idConta: idConta,
        tipo: tipo,
        centavos: centavos,
        vetorConhecido: vetorConhecido ?? this.vetorConhecido,
        criadaEm: criadaEm,
        estado: estado ?? this.estado,
        mensagem: mensagem,
        saldoRealCentavos: saldoRealCentavos,
      );

  Map<String, dynamic> paraArmazenamento() => {
        'idConta': idConta,
        'tipo': tipo,
        'centavos': centavos,
        'vetorConhecido': vetorConhecido,
        'criadaEm': criadaEm.toIso8601String(),
        'estado': estado.name,
        'mensagem': mensagem,
        'saldoRealCentavos': saldoRealCentavos,
      };

  factory OperacaoPendente.doArmazenamento(int chave, Map<dynamic, dynamic> m) => OperacaoPendente(
        chave: chave,
        idConta: m['idConta'] as int,
        tipo: m['tipo'] as String,
        centavos: m['centavos'] as int,
        vetorConhecido: List<int>.from(m['vetorConhecido'] as List),
        criadaEm: DateTime.parse(m['criadaEm'] as String),
        estado: EstadoPendente.values.byName(m['estado'] as String),
        mensagem: m['mensagem'] as String?,
        saldoRealCentavos: m['saldoRealCentavos'] as int?,
      );
}
