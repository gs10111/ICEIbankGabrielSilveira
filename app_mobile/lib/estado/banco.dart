import 'dart:math';

import 'package:flutter/foundation.dart';

import '../api/api_cliente.dart';
import '../dados/armazenamento.dart';
import '../modelo/conta.dart';
import '../modelo/dinheiro.dart';
import '../modelo/operacao_pendente.dart';

class OperacaoRecusadaNoApp implements Exception {
  final String mensagem;

  const OperacaoRecusadaNoApp(this.mensagem);

  @override
  String toString() => mensagem;
}

class Banco extends ChangeNotifier {
  final ApiCliente api;
  final Armazenamento armazenamento;

  Sessao? sessao;
  ContaLida? conta;

  bool offline = false;

  String? avisoDeSessao;

  bool _sincronizando = false;

  Banco(this.api, this.armazenamento);

  bool get logado => sessao != null;

  List<OperacaoPendente> get pendentes =>
      sessao == null ? const [] : armazenamento.pendentes(sessao!.idConta);

  bool get temPendentes =>
      pendentes.any((p) => p.estado == EstadoPendente.pendente);

  int? get saldoProvisorioCentavos {
    if (conta == null) return null;
    return conta!.saldoCentavos +
        pendentes
            .where((p) => p.estado == EstadoPendente.pendente)
            .fold(0, (soma, p) => soma + p.efeitoCentavos);
  }

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

  Future<void> sincronizarPendentes() async {
    if (_sincronizando || sessao == null) return;
    _sincronizando = true;
    try {
      for (final chave in pendentes.map((p) => p.chave).toList()) {
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

  Future<T> _protegido<T>(Future<T> Function() operacao) async {
    try {
      return await operacao();
    } on ErroApi catch (e) {
      if (e.tokenExpirado) sair('Sessão expirada. Entre de novo.');
      rethrow;
    }
  }

  static final _aleatorio = Random.secure();

  static const _maiorInt32 = 0x7fffffff;

  String _novaChave() =>
      'app-${DateTime.now().microsecondsSinceEpoch}-${_aleatorio.nextInt(_maiorInt32).toRadixString(16)}';
}
