import 'dinheiro.dart';

/// Uma leitura da conta: o saldo E o vetor da agencia no momento da leitura.
///
/// Os dois andam juntos de proposito (Sprint 3, Parte E): o vetor e o que permite
/// a agencia dizer, mais tarde, se uma operacao feita offline ainda e segura.
class ContaLida {
  final int id;
  final String nome;
  final int saldoCentavos;
  final int agencia;
  final List<int> vetor;
  final DateTime lidaEm;

  const ContaLida({
    required this.id,
    required this.nome,
    required this.saldoCentavos,
    required this.agencia,
    required this.vetor,
    required this.lidaEm,
  });

  factory ContaLida.daApi(Map<String, dynamic> json) => ContaLida(
        id: json['id'] as int,
        nome: json['nomeAluno'] as String? ?? '',
        saldoCentavos: centavosDe(json['saldo']),
        agencia: json['agencia'] as int,
        vetor: List<int>.from(json['vetorAtual'] as List? ?? const []),
        lidaEm: DateTime.now(),
      );

  factory ContaLida.doArmazenamento(Map<dynamic, dynamic> m) => ContaLida(
        id: m['id'] as int,
        nome: m['nome'] as String,
        saldoCentavos: m['saldoCentavos'] as int,
        agencia: m['agencia'] as int,
        vetor: List<int>.from(m['vetor'] as List),
        lidaEm: DateTime.parse(m['lidaEm'] as String),
      );

  Map<String, dynamic> paraArmazenamento() => {
        'id': id,
        'nome': nome,
        'saldoCentavos': saldoCentavos,
        'agencia': agencia,
        'vetor': vetor,
        'lidaEm': lidaEm.toIso8601String(),
      };
}

class Sessao {
  final String token;
  final int idConta;
  final String nome;
  final int agencia;

  const Sessao({required this.token, required this.idConta, required this.nome, required this.agencia});
}

class Recibo {
  final String mensagem;
  final int saldoOrigemCentavos;

  const Recibo(this.mensagem, this.saldoOrigemCentavos);
}
