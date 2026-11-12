import 'dart:math';

import 'package:flutter/foundation.dart';

import '../api/api_cliente.dart';
import '../dados/armazenamento.dart';
import '../modelo/conta.dart';
import '../modelo/dinheiro.dart';
import '../modelo/operacao_pendente.dart';

/// O app recusou a operacao antes de mandar para a agencia (ex.: saque offline maior
/// que o saldo provisorio, transferencia sem conexao).
class OperacaoRecusadaNoApp implements Exception {
  final String mensagem;

  const OperacaoRecusadaNoApp(this.mensagem);

  @override
  String toString() => mensagem;
}

/// Estado do fluxo bancario: sessao, ultima leitura da conta, fila offline.
///
/// ChangeNotifier puro, sem pacote de gerencia de estado: sao duas telas e um
/// punhado de campos. As telas escutam com ListenableBuilder.
///
/// As operacoes LANCAM a excecao (ErroApi / OperacaoRecusadaNoApp) para a tela
/// mostrar a mensagem; o estado so guarda o que precisa sobreviver entre telas.
///
/// SPRINT 3, Parte F — offline-first:
///  - toda leitura bem-sucedida vai para o aparelho (Armazenamento); sem conexao a
///    tela mostra a ultima;
///  - deposito/saque sem conexao NAO falha: entra na fila com o vetor que o app tinha;
///  - o saldo mostrado e o PROVISORIO (confirmado + pendentes), com aviso visual;
///  - sincronizarPendentes() e chamado pelo GatilhoDeSincronizacao ao reconectar.
class Banco extends ChangeNotifier {
  final ApiCliente api;
  final Armazenamento armazenamento;

  Sessao? sessao;
  ContaLida? conta;

  /// A ultima tentativa de falar com a agencia da conta falhou por rede.
  bool offline = false;

  /// Motivo de a sessao ter acabado sozinha (401), para a tela de login explicar.
  String? avisoDeSessao;

  bool _sincronizando = false;

  Banco(this.api, this.armazenamento);

  bool get logado => sessao != null;

  List<OperacaoPendente> get pendentes =>
      sessao == null ? const [] : armazenamento.pendentes(sessao!.idConta);

  bool get temPendentes =>
      pendentes.any((p) => p.estado == EstadoPendente.pendente);

  /// Saldo confirmado pela agencia + efeito das operacoes que ainda vao ser sincronizadas.
  /// Conflitos e recusadas ficam de fora: a agencia ja disse que NAO aplicou.
  int? get saldoProvisorioCentavos {
    if (conta == null) return null;
    return conta!.saldoCentavos +
        pendentes
            .where((p) => p.estado == EstadoPendente.pendente)
            .fold(0, (soma, p) => soma + p.efeitoCentavos);
  }

  /// Reabre a sessao guardada no aparelho — o app abre logado mesmo em modo aviao.
  void restaurar() {
    final guardada = armazenamento.sessao;
    if (guardada == null) return;
    sessao = guardada;
    api.token = guardada.token;
    conta = armazenamento.conta(guardada.idConta);
    notifyListeners();
  }

  Future<void> entrar(int idConta, String senha) async {
    final nova = await api.login(idConta, senha);
    api.token = nova.token;
    sessao = nova;
    avisoDeSessao = null;
    conta = armazenamento.conta(idConta);
    await armazenamento.guardarSessao(nova, api.malha.host);
    notifyListeners();
    await atualizar();
  }

  /// Sem conexao, NAO lanca: fica com a ultima leitura guardada e marca `offline`.
  Future<void> atualizar() => _protegido(() async {
    try {
      await _guardar(await api.consultar(sessao!.idConta));
      offline = false;
    } on SemConexao {
      offline = true;
      conta ??= armazenamento.conta(sessao!.idConta);
    }
    notifyListeners();
  });

  Future<String> depositar(int centavos) => _escrever('DEPOSITO', centavos);

  Future<String> sacar(int centavos) => _escrever('SAQUE', centavos);

  /// Transferencia NAO entra na fila: o endpoint de sincronizacao so cobre deposito e
  /// saque, e uma transferencia mexe em duas contas, possivelmente em duas agencias.
  Future<String> transferir(int idDestino, int centavos) =>
      _protegido(() async {
        try {
          final recibo = await api.transferir(
            sessao!.idConta,
            idDestino,
            centavos,
            _novaChave(),
          );
          await atualizar();
          return recibo.mensagem;
        } on SemConexao {
          offline = true;
          notifyListeners();
          throw const OperacaoRecusadaNoApp(
            'Transferência exige conexão com a agência: ela não é guardada para depois.',
          );
        }
      });

  Future<String> _escrever(String tipo, int centavos) => _protegido(() async {
    // Com fila nao vazia, a operacao nova vai para o FIM da fila mesmo online:
    // aplicar direto avancaria o vetor e faria todas as pendentes darem 409.
    if (!temPendentes) {
      try {
        final lida = tipo == 'DEPOSITO'
            ? await api.depositar(sessao!.idConta, centavos)
            : await api.sacar(sessao!.idConta, centavos);
        await _guardar(lida);
        offline = false;
        notifyListeners();
        return '${tipo == 'DEPOSITO' ? 'Depósito' : 'Saque'} de ${formatarReais(centavos)} aplicado.';
      } on SemConexao {
        offline = true;
      }
    }
    return _enfileirar(tipo, centavos);
  });

  Future<String> _enfileirar(String tipo, int centavos) async {
    final base = conta;
    if (base == null || base.vetor.isEmpty) {
      throw const OperacaoRecusadaNoApp(
        'Sem conexão e sem nenhuma leitura da conta guardada no aparelho.',
      );
    }
    // A UI otimista mostra o saque como feito; nao deixa mostrar saldo negativo.
    if (tipo == 'SAQUE' && saldoProvisorioCentavos! < centavos) {
      throw OperacaoRecusadaNoApp(
        'Saldo provisório insuficiente (${formatarReais(saldoProvisorioCentavos!)}) para sacar offline.',
      );
    }
    await armazenamento.enfileirar(sessao!.idConta, tipo, centavos, base.vetor);
    notifyListeners();
    return '${tipo == 'DEPOSITO' ? 'Depósito' : 'Saque'} de ${formatarReais(centavos)} guardado: '
        'pendente de sincronização.';
  }

  /// Entrega a fila, na ordem. Para no primeiro sinal de rede caida.
  Future<void> sincronizarPendentes() async {
    if (_sincronizando || sessao == null) return;
    _sincronizando = true;
    try {
      for (final chave in pendentes.map((p) => p.chave).toList()) {
        // Rele do disco: o sucesso anterior pode ter trocado o vetor desta.
        final op = armazenamento.pendente(chave);
        if (op == null || op.estado != EstadoPendente.pendente) continue;
        try {
          final lida = await api.sincronizar(
            op.idConta,
            op.tipo,
            op.centavos,
            op.vetorConhecido,
          );
          await armazenamento.remover(op);
          await _guardar(lida);
          await _rebasear(de: op.vetorConhecido, para: lida.vetor);
          offline = false;
        } on ConflitoDeSincronizacao catch (c) {
          await _guardar(c.contaAtual);
          await armazenamento.substituir(
            op.com(
              estado: EstadoPendente.conflito,
              mensagem: c.mensagem,
              saldoRealCentavos: c.contaAtual.saldoCentavos,
            ),
          );
          offline = false;
        } on SemConexao {
          offline = true;
          break;
        } on ErroApi catch (e) {
          if (e.tokenExpirado) {
            sair(
              'Sessão expirada. Entre de novo — as operações pendentes continuam guardadas.',
            );
            return;
          }
          await armazenamento.substituir(
            op.com(estado: EstadoPendente.recusada, mensagem: e.mensagem),
          );
        }
        notifyListeners();
      }
    } finally {
      _sincronizando = false;
      notifyListeners();
    }
  }

  /// A pessoa leu o saldo real e decidiu mandar de novo: vai com o vetor que veio no 409.
  Future<void> reenviar(OperacaoPendente op) async {
    await armazenamento.substituir(
      op.com(estado: EstadoPendente.pendente, vetorConhecido: conta!.vetor),
    );
    notifyListeners();
    await sincronizarPendentes();
  }

  Future<void> descartar(OperacaoPendente op) async {
    await armazenamento.remover(op);
    notifyListeners();
  }

  void sair([String? motivo]) {
    sessao = null;
    conta = null;
    api.token = null;
    offline = false;
    avisoDeSessao = motivo;
    armazenamento.limparSessao();
    notifyListeners();
  }

  Future<void> _guardar(ContaLida lida) async {
    conta = lida;
    await armazenamento.guardarConta(lida);
  }

  /// Varias operacoes offline foram feitas sobre a MESMA leitura, entao carregam o
  /// mesmo vetor. Quando a primeira e aplicada, o vetor da agencia avanca — e a mudanca
  /// foi a propria operacao do app, que ele sabe exatamente qual foi. As seguintes
  /// passam a carregar o vetor novo; sem isto, so a primeira passaria e o resto daria
  /// 409 por causa de si mesmo.
  Future<void> _rebasear({
    required List<int> de,
    required List<int> para,
  }) async {
    for (final outra in pendentes) {
      if (outra.estado == EstadoPendente.pendente &&
          listEquals(outra.vetorConhecido, de)) {
        await armazenamento.substituir(outra.com(vetorConhecido: para));
      }
    }
  }

  /// 401 em qualquer operacao derruba a sessao: o token de 15 min expirou.
  Future<T> _protegido<T>(Future<T> Function() operacao) async {
    try {
      return await operacao();
    } on ErroApi catch (e) {
      if (e.tokenExpirado) sair('Sessão expirada. Entre de novo.');
      rethrow;
    }
  }

  static final _aleatorio = Random.secure();

  String _novaChave() =>
      'app-${DateTime.now().microsecondsSinceEpoch}-${_aleatorio.nextInt(1 << 32).toRadixString(16)}';
}
