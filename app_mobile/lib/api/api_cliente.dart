import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../modelo/conta.dart';
import '../modelo/dinheiro.dart';
import '../modelo/malha.dart';

/// A agencia respondeu com erro (4xx/5xx). A mensagem e a do backend, campo `erro`.
class ErroApi implements Exception {
  final int status;
  final String mensagem;

  const ErroApi(this.status, this.mensagem);

  bool get tokenExpirado => status == 401;

  @override
  String toString() => mensagem;
}

/// A agencia NAO respondeu: sem rede, modo aviao, agencia derrubada ou timeout.
/// E diferente de ErroApi — aqui a operacao nem chegou a ser julgada.
class SemConexao implements Exception {
  final int agencia;

  const SemConexao(this.agencia);

  @override
  String toString() => 'Agência $agencia não respondeu (sem conexão ou fora do ar).';
}

/// Unico ponto do app que fala HTTP e que sabe do JWT — o mesmo papel do
/// modelo/api.js do frontend web (resposta da pergunta 12.3.1 do Sprint 1).
class ApiCliente {
  Malha malha;
  final http.Client _http;
  final Duration timeout;
  String? token;

  ApiCliente(this.malha, {http.Client? cliente, this.timeout = const Duration(seconds: 4)})
      : _http = cliente ?? http.Client();

  Future<Sessao> login(int idConta, String senha) async {
    final agencia = malha.agenciaDaConta(idConta);
    final corpo = await _requisitar(agencia, 'POST', '/auth/login', {'idConta': idConta, 'senha': senha});
    return Sessao(
      token: corpo['token'] as String,
      idConta: corpo['idConta'] as int,
      nome: corpo['nomeAluno'] as String,
      agencia: corpo['agencia'] as int,
    );
  }

  Future<ContaLida> consultar(int idConta) async =>
      ContaLida.daApi(await _requisitar(malha.agenciaDaConta(idConta), 'GET', '/contas/$idConta'));

  Future<ContaLida> depositar(int idConta, int centavos) async => ContaLida.daApi(await _requisitar(
      malha.agenciaDaConta(idConta), 'POST', '/contas/$idConta/depositar', {'valor': reaisDe(centavos)}));

  Future<ContaLida> sacar(int idConta, int centavos) async => ContaLida.daApi(await _requisitar(
      malha.agenciaDaConta(idConta), 'POST', '/contas/$idConta/sacar', {'valor': reaisDe(centavos)}));

  /// A chave de idempotencia e gerada AQUI, no cliente: se o servidor gerasse, cada
  /// reenvio teria chave nova e a deduplicacao do Sprint 1 nao serviria para nada.
  Future<Recibo> transferir(int idOrigem, int idDestino, int centavos, String chave) async {
    final corpo = await _requisitarCom(malha.agenciaDaConta(idOrigem), 'POST', '/transferencias',
        {'idOrigem': idOrigem, 'idDestino': idDestino, 'valor': reaisDe(centavos)},
        {'Idempotency-Key': chave});
    return Recibo(corpo['mensagem'] as String, centavosDe(corpo['saldoOrigem']));
  }

  /// GET /bully/lider numa agencia especifica. Publico: nao precisa de login.
  /// null = aquela agencia ainda nao sabe quem e a lider (eleicao em curso).
  Future<int?> liderSegundo(int agencia) async =>
      (await _requisitar(agencia, 'GET', '/bully/lider'))['liderAtual'] as int?;

  Future<Map<String, dynamic>> _requisitar(int agencia, String metodo, String caminho,
      [Map<String, dynamic>? corpo]) async => _requisitarCom(agencia, metodo, caminho, corpo, const {});

  Future<Map<String, dynamic>> _requisitarCom(int agencia, String metodo, String caminho,
      Map<String, dynamic>? corpo, Map<String, String> cabecalhos) async {
    final requisicao = http.Request(metodo, malha.url(agencia, caminho))
      ..headers.addAll({'Content-Type': 'application/json', ...cabecalhos});
    if (token != null) requisicao.headers['Authorization'] = 'Bearer $token';
    if (corpo != null) requisicao.body = jsonEncode(corpo);

    final http.Response resposta;
    try {
      resposta = await http.Response.fromStream(await _http.send(requisicao).timeout(timeout));
    } on TimeoutException {
      throw SemConexao(agencia);
    } on http.ClientException {
      throw SemConexao(agencia);
    }

    final json = resposta.body.isEmpty ? <String, dynamic>{} : jsonDecode(utf8.decode(resposta.bodyBytes));
    if (resposta.statusCode >= 200 && resposta.statusCode < 300) {
      return json as Map<String, dynamic>;
    }
    throw erroDe(resposta.statusCode, json);
  }

  /// Separado para a Parte F poder especializar o 409 da sincronizacao.
  Exception erroDe(int status, Object? json) {
    final mensagem = json is Map && json['erro'] != null ? json['erro'] as String : 'Erro HTTP $status';
    return ErroApi(status, mensagem);
  }
}
