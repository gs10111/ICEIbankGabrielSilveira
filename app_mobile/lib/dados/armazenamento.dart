import 'dart:typed_data';

import 'package:hive_ce/hive_ce.dart';

import '../modelo/conta.dart';
import '../modelo/operacao_pendente.dart';

/// SPRINT 3, Parte F — o que o app guarda no aparelho. Hive (hive_ce).
///
/// Tres caixas:
///   contas     ultima leitura de cada conta (saldo + vetorAtual), por id
///   pendentes  a fila; a chave e o autoincremento do Hive, entao a ordem de
///              insercao e a ordem de sincronizacao
///   sessao     token, conta e host — para o app reabrir offline ja logado
///
/// Por que Hive e nao sqflite: os dados sao poucos e sem relacao entre si (um
/// mapa por conta, uma lista de operacoes), nao ha consulta a fazer — e Hive roda
/// igual no Android, na web e no `flutter test`, sem banco nativo. Ver 10.3.1.
class Armazenamento {
  final Box<Map> _contas;
  final Box<Map> _pendentes;
  final Box<dynamic> _sessao;

  Armazenamento._(this._contas, this._pendentes, this._sessao);

  /// `pasta` so nos testes; no app, o main chama Hive.initFlutter() antes.
  static Future<Armazenamento> abrir([String? pasta]) async {
    if (pasta != null) Hive.init(pasta);
    return Armazenamento._(
      await Hive.openBox<Map>('contas'),
      await Hive.openBox<Map>('pendentes'),
      await Hive.openBox<dynamic>('sessao'),
    );
  }

  /// Para testes de widget: caixas so em memoria. Disco de verdade dentro do relogio
  /// falso do testWidgets nunca completa a escrita.
  static Future<Armazenamento> emMemoria() async {
    final vazio = Uint8List(0);
    return Armazenamento._(
      await Hive.openBox<Map>('contas', bytes: vazio),
      await Hive.openBox<Map>('pendentes', bytes: vazio),
      await Hive.openBox<dynamic>('sessao', bytes: vazio),
    );
  }

  // ---------------------------------------------------------------- contas

  ContaLida? conta(int id) {
    final m = _contas.get(id);
    return m == null ? null : ContaLida.doArmazenamento(m);
  }

  Future<void> guardarConta(ContaLida conta) =>
      _contas.put(conta.id, conta.paraArmazenamento());

  // ------------------------------------------------------------- pendentes

  List<OperacaoPendente> pendentes(int idConta) => [
    for (final chave in _pendentes.keys.cast<int>().toList()..sort())
      if (_pendentes.get(chave)!['idConta'] == idConta)
        OperacaoPendente.doArmazenamento(chave, _pendentes.get(chave)!),
  ];

  OperacaoPendente? pendente(int chave) {
    final m = _pendentes.get(chave);
    return m == null ? null : OperacaoPendente.doArmazenamento(chave, m);
  }

  Future<void> enfileirar(
    int idConta,
    String tipo,
    int centavos,
    List<int> vetorConhecido,
  ) => _pendentes.add({
    'idConta': idConta,
    'tipo': tipo,
    'centavos': centavos,
    'vetorConhecido': vetorConhecido,
    'criadaEm': DateTime.now().toIso8601String(),
    'estado': EstadoPendente.pendente.name,
  });

  Future<void> substituir(OperacaoPendente op) =>
      _pendentes.put(op.chave, op.paraArmazenamento());

  Future<void> remover(OperacaoPendente op) => _pendentes.delete(op.chave);

  // ---------------------------------------------------------------- sessao

  Sessao? get sessao {
    final token = _sessao.get('token') as String?;
    if (token == null) return null;
    return Sessao(
      token: token,
      idConta: _sessao.get('idConta') as int,
      nome: _sessao.get('nome') as String,
      agencia: _sessao.get('agencia') as int,
    );
  }

  String? get host => _sessao.get('host') as String?;

  Future<void> guardarSessao(Sessao s, String host) => _sessao.putAll({
    'token': s.token,
    'idConta': s.idConta,
    'nome': s.nome,
    'agencia': s.agencia,
    'host': host,
  });

  /// Apaga o token, mas guarda o host: na proxima abertura o campo ja vem certo.
  Future<void> limparSessao() =>
      _sessao.deleteAll(['token', 'idConta', 'nome', 'agencia']);
}
